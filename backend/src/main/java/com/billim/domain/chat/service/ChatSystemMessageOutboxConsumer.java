package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.dto.SystemPayloadResponse;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.repository.ChatRoomAppointmentReadRepository;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.service.EventPayload;
import com.billim.domain.notification.service.OutboxConsumer;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.global.json.JsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** 커밋된 거래·약속 사건을 연결된 채팅방의 SYSTEM 메시지로 기록한다. */
@Component
@RequiredArgsConstructor
public class ChatSystemMessageOutboxConsumer implements OutboxConsumer {

    private static final String CONSUMER_KEY_PREFIX = "room:";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter APPOINTMENT =
        DateTimeFormatter.ofPattern("M/d(E) a h:mm", Locale.KOREAN);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RentalRepository rentalRepository;
    private final ChatRoomAppointmentReadRepository appointmentReadRepository;
    private final ChatSystemMessageService systemMessageService;
    private final JsonCodec jsonCodec;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<String> consumerKeys(OutboxEvent event) {
        Target target = target(event);
        return target == null ? List.of() : List.of(CONSUMER_KEY_PREFIX + target.roomId());
    }

    @Override
    public void consume(OutboxEvent event, String consumerKey) {
        Target target = target(event);
        if (target == null || !consumerKey.equals(CONSUMER_KEY_PREFIX + target.roomId())) {
            throw new IllegalStateException("사건의 채팅방 연결이 변경됐습니다: eventId=" + event.getId());
        }

        EventPayload payload = jsonCodec.read(event.getPayload(), EventPayload.class);
        SystemPayloadResponse system = new SystemPayloadResponse(
            event.getEventType().name(),
            event.getAggregateVersion(),
            event.getCreatedAt(),
            label(event.getEventType(), payload)
        );
        ChatMessage message = systemMessageService.append(
            target.roomId(), target.rentalId(), null, event.getId(), system
        );
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(
            target.roomId(), MessageResponse.from(message)
        ));
    }

    private Target target(OutboxEvent event) {
        Long rentalId = switch (event.getAggregateType()) {
            case RENTAL -> event.getAggregateId();
            case APPOINTMENT_SLOT -> appointmentReadRepository.findById(event.getAggregateId())
                .map(slot -> slot.getRentalId())
                .orElse(null);
            case REPORT -> null;
        };
        if (rentalId == null) {
            return null;
        }
        return rentalRepository.findById(rentalId)
            .map(rental -> linkedTarget(rental))
            .orElse(null);
    }

    private Target linkedTarget(Rental rental) {
        return rental.getChatRoomId() == null
            ? null
            : new Target(rental.getId(), rental.getChatRoomId());
    }

    private String label(EventType type, EventPayload payload) {
        EventPayload.Display display = payload.display();
        String actor = display.actorName() == null || display.actorName().isBlank()
            ? "이웃" : display.actorName();
        String message = switch (type) {
            case RENTAL_REQUESTED -> "대여 요청을 보냈어요" + period(display);
            case RENTAL_APPROVED -> actor + " 님이 대여 요청을 승인했어요";
            case RENTAL_REJECTED -> actor + " 님이 대여 요청을 거절했어요";
            case RENTAL_CANCELED -> actor + " 님이 대여 요청을 취소했어요";
            case RENTAL_HANDED_OVER -> "물건 전달이 확인됐어요";
            case RENTAL_RETURNED -> "물건 반납이 확인됐어요";
            case RENTAL_EXPIRED -> "대여 요청이 마감됐어요";
            case APPOINTMENT_PROPOSED -> actor + " 님이 약속을 제안했어요" + when(display);
            case APPOINTMENT_CONFIRMED -> "약속이 확정됐어요" + when(display);
            case APPOINTMENT_WITHDRAWN -> actor + " 님이 약속 제안을 철회했어요";
            case REPORT_RESOLVED -> throw new IllegalArgumentException("신고 사건은 채팅 메시지 대상이 아닙니다.");
        };
        return message.length() <= 200 ? message : message.substring(0, 200);
    }

    private String period(EventPayload.Display display) {
        if (display.startDate() == null || display.endDate() == null) {
            return "";
        }
        return " · %s – %s".formatted(
            display.startDate().format(DATE), display.endDate().format(DATE)
        );
    }

    private String when(EventPayload.Display display) {
        Instant appointmentAt = display.appointmentAt();
        return appointmentAt == null
            ? ""
            : " · " + APPOINTMENT.format(appointmentAt.atZone(KST));
    }

    private record Target(Long rentalId, Long roomId) {
    }
}
