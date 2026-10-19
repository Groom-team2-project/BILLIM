package com.billim.domain.rental.service;

import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.service.*;
import com.billim.global.entity.IdempotencyRecord;
import com.billim.global.repository.IdempotencyRecordRepository;
import com.billim.global.json.JsonCodec;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** 회원 잠금 아래 거래·이력·사건·성공 응답의 원자적 저장. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RentalRequestEffects {
    private static final String CREATE = "POST /api/v1/rentals";
    private final IdempotencyRecordRepository records;
    private final DomainEventPublisher events;
    private final JsonCodec json;

    public Optional<RentalDetailStatusResponse> findReplay(long memberId, String key,
                                                          CreateRentalRequest request, Instant now) {
        return replay(memberId, CREATE, key, createBody(request), 201, now, RentalDetailStatusResponse.class);
    }

    public void recordSuccess(long memberId, String key, CreateRentalRequest request,
                              Rental rental, RentalDetailStatusResponse response, Instant now) {
        record(memberId, CREATE, key, createBody(request), 201, rental, response, EventType.RENTAL_REQUESTED, now);
    }

    public Optional<RentalDetailStatusResponse> findCommandReplay(long memberId, String key,
                                                                  RentalCommand command, Instant now) {
        return replay(memberId, operation(command), key, commandBody(command), 200, now, RentalDetailStatusResponse.class);
    }

    public void recordCommandSuccess(long memberId, String key, RentalCommand command,
                                     Rental rental, RentalDetailStatusResponse response, Instant now) {
        EventType type = switch (command.action()) {
            case APPROVE -> EventType.RENTAL_APPROVED;
            case REJECT -> EventType.RENTAL_REJECTED;
            case CANCEL -> EventType.RENTAL_CANCELED;
            case HANDOVER -> EventType.RENTAL_HANDED_OVER;
            case RETURN -> EventType.RENTAL_RETURNED;
        };
        record(memberId, operation(command), key, commandBody(command), 200, rental, response, type, now);
    }

    public Optional<AppointmentSlotResponse> findAppointmentReplay(long memberId, String operation,
            String key, Object body, int status, Instant now) {
        return replay(memberId, operation, key, body, status, now, AppointmentSlotResponse.class);
    }

    public void recordAppointmentSuccess(long memberId, String operation, String key, Object body,
            int status, AppointmentSlotResponse response, Instant now) {
        records.saveAndFlush(IdempotencyRecord.succeeded(memberId, operation, key, hash(body),
                status, json.write(response), now.plus(Duration.ofHours(24))));
    }

    private <T> Optional<T> replay(long memberId, String operation, String key,
                                   Object body, int status, Instant now, Class<T> responseType) {
        var found = records.findByMemberIdAndOperationKeyAndRequestKey(memberId, operation, key);
        if (found.isEmpty()) return Optional.empty();
        var record = found.get();
        if (!record.getExpiresAt().isAfter(now)) {
            records.delete(record);
            records.flush();
            return Optional.empty();
        }
        if (!record.getRequestHash().equals(hash(body))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        if (record.getHttpStatus() != status) throw new IllegalStateException("저장된 성공 응답 코드 불일치");
        return Optional.of(json.read(record.getResponseBody(), responseType));
    }

    private void record(long memberId, String operation, String key, Object body, int status,
                        Rental rental, RentalDetailStatusResponse response, EventType type, Instant now) {
        var summary = response.rental();
        boolean actorIsOwner = rental.getOwnerId().equals(memberId);
        var recipient = EventPayload.Recipient.of(
                actorIsOwner ? rental.getBorrowerId() : rental.getOwnerId(),
                actorIsOwner ? RecipientRole.BORROWER : RecipientRole.OWNER);
        var actor = actorIsOwner ? summary.owner() : summary.borrower();
        events.publish(DomainEvent.of(type, rental.getId(), rental.getVersion(),
                EventPayload.forRental(rental.getId(), List.of(recipient),
                        EventPayload.Display.ofRental(actor.displayName(), summary.itemTitle(),
                                summary.startDate(), summary.endDate()))));
        records.saveAndFlush(IdempotencyRecord.succeeded(memberId, operation, key, hash(body),
                status, json.write(response), now.plus(Duration.ofHours(24))));
    }

    private Object createBody(CreateRentalRequest request) {
        return new CreateRentalRequest(Long.toString(Long.parseLong(request.itemId())),
                request.startDate(), request.endDate());
    }

    private Object commandBody(RentalCommand command) {
        return switch (command.action()) {
            case REJECT -> new RejectRentalRequest(command.expectedVersion(), command.reason());
            case CANCEL -> new CancelRentalRequest(command.expectedVersion(), command.reason());
            default -> new RentalVersionRequest(command.expectedVersion());
        };
    }

    private String operation(RentalCommand command) {
        return CREATE + "/" + command.rentalId() + "/" + command.action().name().toLowerCase(Locale.ROOT);
    }

    private String hash(Object body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.write(body).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }
}
