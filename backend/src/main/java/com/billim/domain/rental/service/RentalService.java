package com.billim.domain.rental.service;

import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.entity.RentalStatusHistory;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.rental.repository.RentalStatusHistoryRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.dto.MemberSummaryResponse;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.service.IdParser;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class RentalService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<RentalStatus> OCCUPYING_STATUSES =
            List.of(RentalStatus.APPROVED, RentalStatus.ACTIVE, RentalStatus.RETURNED);

    private final RentalRepository rentals;
    private final RentalStatusHistoryRepository histories;
    private final ItemRepository items;
    private final MemberRepository members;
    private final RentalRequestPolicy policy;
    private final RentalRequestEffects effects;
    private final Clock clock;

    public RentalService(RentalRepository rentals, RentalStatusHistoryRepository histories,
                         ItemRepository items, MemberRepository members, RentalRequestPolicy policy,
                         RentalRequestEffects effects, Clock clock) {
        this.rentals = rentals;
        this.histories = histories;
        this.items = items;
        this.members = members;
        this.policy = policy;
        this.effects = effects;
        this.clock = clock;
    }

    @Transactional
    public RentalDetailStatusResponse createRental(
        String idempotencyKey,
        CreateRentalRequest request,
        LoginMember member) {

        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (idempotencyKey == null || !idempotencyKey.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (request == null || request.startDate() == null || request.endDate() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long itemId = IdParser.parse(request.itemId());
        String key = idempotencyKey.toLowerCase(Locale.ROOT);
        // 공통 명세의 회원 → 물건 잠금 순서. 재전송과 최초 실행을 직렬화.
        Member borrower = members.findByIdForUpdate(member.memberId())
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        Instant now = clock.instant();
        var replay = effects.findReplay(borrower.getId(), key, request, now);
        if (replay.isPresent()) {
            RentalDetailStatusResponse response = replay.get();
            // 저장된 응답은 현재 명령 조건 대신 기존 거래 조회 권한으로 재검사.
            Rental original = rentals.findById(Long.valueOf(response.rental().id()))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!original.getBorrowerId().equals(borrower.getId())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            return response;
        }

        LocalDate today = LocalDate.ofInstant(now, KST);
        validateDates(request.startDate(), request.endDate(), today);
        Item item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        policy.requireViewable(item, borrower.getId());
        if (item.isDeleted() || item.getVisibility() != ItemVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (item.isOwnedBy(borrower.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String placeName = policy.requireRequestAllowed(item, borrower.getId());
        policy.requireAvailable(item);
        if (placeName == null || placeName.isBlank() || placeName.length() > 100) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        if (request.startDate().isBefore(item.getAvailableStartDate())
                || request.endDate().isAfter(item.getAvailableEndDate())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "물건의 대여 가능 기간을 벗어났습니다.");
        }
        if (rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(itemId, RentalStatus.ACTIVE, now)) {
            throw new BusinessException(ErrorCode.ITEM_OVERDUE);
        }
        if (rentals.hasOccupiedDates(itemId, OCCUPYING_STATUSES, request.startDate(), request.endDate())) {
            throw new BusinessException(ErrorCode.RENTAL_DATE_CONFLICT);
        }
        if (rentals.hasPendingRequest(itemId, borrower.getId(), request.startDate(), request.endDate())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RENTAL_REQUEST);
        }
        Member owner = members.findById(item.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Rental rental = rentals.saveAndFlush(Rental.request(item.getId(), item.getOwnerId(), borrower.getId(),
                item.getCommunityId(), request.startDate(), request.endDate(),
                request.startDate().plusDays(1).atStartOfDay(KST).toInstant(),
                request.endDate().plusDays(1).atStartOfDay(KST).toInstant(),
                item.getTitle(), item.getPlaceId(), placeName));
        RentalStatusHistory history = histories.saveAndFlush(RentalStatusHistory.requested(rental));
        RentalDetailStatusResponse response = createdResponse(rental, history, owner, borrower, today);
        effects.recordSuccess(borrower.getId(), key, request, rental, response, now);
        return response;
    }

    private void validateDates(LocalDate start, LocalDate end, LocalDate today) {
        if (start.isBefore(today) || start.isAfter(end) || end.isAfter(LocalDate.of(9999, 12, 30))
                || ChronoUnit.DAYS.between(start, end) >= 30) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "대여 기간은 오늘부터 시작일·종료일을 포함해 최대 30일까지 가능합니다.");
        }
    }

    private RentalDetailStatusResponse createdResponse(Rental rental, RentalStatusHistory history,
                                                       Member owner, Member borrower, LocalDate today) {
        var summary = new RentalSummaryResponse(String.valueOf(rental.getId()),
                String.valueOf(rental.getItemId()), rental.getItemTitleSnapshot(),
                new MemberSummaryResponse(String.valueOf(owner.getId()), owner.getDisplayName(), owner.getCreatedAt()),
                new MemberSummaryResponse(String.valueOf(borrower.getId()), borrower.getDisplayName(), borrower.getCreatedAt()),
                rental.getStartDate(), rental.getEndDate(), rental.getStatus(), false,
                Math.toIntExact(ChronoUnit.DAYS.between(today, rental.getStartDate())),
                rental.getPlaceNameSnapshot(), rental.getVersion(), List.of("CANCEL"), rental.getCreatedAt());
        var initialHistory = new RentalDetailStatusResponse.History(null, history.getToStatus(),
                String.valueOf(history.getActorId()), null, history.getRentalVersion(), history.getCreatedAt());
        return new RentalDetailStatusResponse(summary, null, rental.getRequestExpiresAt(), rental.getDueAt(),
                null, null, null, null, List.of(initialHistory), List.of());
    }

    @Transactional
    public RentalDetailStatusResponse cancelRental(
        Long rentalId,
        String idempotencyKey,
        CancelRentalRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse approveRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse rejectRental(
        Long rentalId,
        String idempotencyKey,
        RejectRentalRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse handoverRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }

    @Transactional
    public RentalDetailStatusResponse returnRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        throw new UnsupportedOperationException("대여 기능 구현 예정");
    }
}
