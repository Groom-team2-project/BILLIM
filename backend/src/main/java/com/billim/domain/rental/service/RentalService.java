package com.billim.domain.rental.service;

import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.AppointmentProposal;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.entity.RentalStatusHistory;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.rental.repository.RentalStatusHistoryRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.service.IdParser;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
import com.billim.global.exception.RentalDateConflictException;
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
import java.util.TreeSet;

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
    private final RentalQueryService queries;

    public RentalService(RentalRepository rentals, RentalStatusHistoryRepository histories,
                         ItemRepository items, MemberRepository members, RentalRequestPolicy policy,
                         RentalRequestEffects effects, Clock clock, RentalQueryService queries) {
        this.rentals = rentals;
        this.histories = histories;
        this.items = items;
        this.members = members;
        this.policy = policy;
        this.effects = effects;
        this.clock = clock;
        this.queries = queries;
    }

    @Transactional
    public RentalDetailStatusResponse createRental(
        String idempotencyKey,
        CreateRentalRequest request,
        LoginMember member) {

        /** 로그인 및 입력 형식 확인 */
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
        /** 문자열 물건 ID를 숫자로 변환하고, UUID 대소문자 차이 제거 */
        long itemId = IdParser.parse(request.itemId());
        String key = idempotencyKey.toLowerCase(Locale.ROOT);
        /** 요청자 행 잠금 및 활성 회원 확인 */
        // 외래 키 검사도 상대 회원을 잠그므로 양측 회원 ID순 → 물건 순서로 통일.
        Long ownerId = items.findOwnerId(itemId).orElse(null);
        Member borrower = lockParticipants(member.memberId(), ownerId);
        /** 이미 처리한 요청이면 이전 응답 반환(회원의 같은 멱등 키로 성공한 요청인지 확인) */
        Instant now = clock.instant();
        var replay = effects.findReplay(borrower.getId(), key, request, now);
        /** 키만 같고 본문이 다르면 오류로 처리 */
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

        /** 기본 날짜 검증 */
        LocalDate today = LocalDate.ofInstant(now, KST);
        validateDates(request.startDate(), request.endDate(), today);
        /** 물건 잠금 및 요청 권한 확인 */
        Item item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        policy.requireViewable(item, borrower.getId());
        if (item.isDeleted() || item.getVisibility() != ItemVisibility.PUBLIC) { // 삭제되거나 비공개인 물건 거부
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (item.isOwnedBy(borrower.getId())) { // 자기 물건 대여 요청 거부
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String placeName = policy.requireRequestAllowed(item, borrower.getId()); // 동네·차단·제재·장소 정책 확인
        policy.requireAvailable(item);
        if (placeName == null || placeName.isBlank() || placeName.length() > 100) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        /** 해당 물건의 일정 확인 */
        if (request.startDate().isBefore(item.getAvailableStartDate()) // 소유자가 설정한 대여 가능 기간 밖이면 거부
                || request.endDate().isAfter(item.getAvailableEndDate())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "물건의 대여 가능 기간을 벗어났습니다.");
        }
        if (rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(itemId, RentalStatus.ACTIVE, now)) { //연체 상태이면 거부
            throw new BusinessException(ErrorCode.ITEM_OVERDUE);
        }
        if (rentals.hasOccupiedDates(itemId, OCCUPYING_STATUSES, request.startDate(), request.endDate())) { // 승인·진행·반납 완료의 약정 기간과 겹치면 거부
            throw new BusinessException(ErrorCode.RENTAL_DATE_CONFLICT);
        }
        if (rentals.hasPendingRequest(itemId, borrower.getId(), request.startDate(), request.endDate())) { //개인의 중복 요청 거부
            throw new BusinessException(ErrorCode.DUPLICATE_RENTAL_REQUEST);
        }
        /** 거래와 최초 상태 이력 저장 */
        Member owner = members.findById(item.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Rental rental = rentals.saveAndFlush(Rental.request(item.getId(), item.getOwnerId(), borrower.getId(),
                item.getCommunityId(), request.startDate(), request.endDate(),
                request.startDate().plusDays(1).atStartOfDay(KST).toInstant(), // 시작일 다음 날 KST 00:00
                request.endDate().plusDays(1).atStartOfDay(KST).toInstant(), // 종료일 다음 날 KST 00:00
                item.getTitle(), item.getPlaceId(), placeName));
        /** 최초 요청 상태 이력 저장 */
        RentalStatusHistory history = histories.saveAndFlush(RentalStatusHistory.requested(rental));
        /** 응답 생성 및 후속 기록 저장 */
        RentalDetailStatusResponse response = createdResponse(rental, history, owner, borrower, today);
        /** 이벤트와 재전송용 성공 응답을 같은 트랜잭션에 저장한 뒤 반환하는 구조 */
        effects.recordSuccess(borrower.getId(), key, request, rental, response, now);
        return response;
    }

    /** 한국 날짜로 변환한 뒤 확인 */
    private void validateDates(LocalDate start, LocalDate end, LocalDate today) {
        if (start.isBefore(today) || start.isAfter(end) || end.isAfter(LocalDate.of(9999, 12, 30))
                || ChronoUnit.DAYS.between(start, end) >= 30) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "대여 기간은 오늘부터 시작일·종료일을 포함해 최대 30일까지 가능합니다.");
        }
    }

    /** 저장된 거래 정보를 FE에 보낼 응답 형태로 조립하는 보조 메서드 */
    private RentalDetailStatusResponse createdResponse(Rental rental, RentalStatusHistory history,
                                                       Member owner, Member borrower, LocalDate today) {
        var summary = new RentalSummaryResponse(String.valueOf(rental.getId()),
                String.valueOf(rental.getItemId()), rental.getItemTitleSnapshot(),
                MemberSummaryResponse.from(owner),
                MemberSummaryResponse.from(borrower),
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

        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return execute(rentalId, idempotencyKey, request.expectedVersion(), request.reason(),
                RentalCommand.Action.CANCEL, member);
    }

    @Transactional
    public RentalDetailStatusResponse approveRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return execute(rentalId, idempotencyKey, request.expectedVersion(), null,
                RentalCommand.Action.APPROVE, member);
    }

    @Transactional
    public RentalDetailStatusResponse rejectRental(
        Long rentalId,
        String idempotencyKey,
        RejectRentalRequest request,
        LoginMember member) {

        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return execute(rentalId, idempotencyKey, request.expectedVersion(), request.reason(),
                RentalCommand.Action.REJECT, member);
    }

    @Transactional
    public RentalDetailStatusResponse handoverRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return execute(rentalId, idempotencyKey, request.expectedVersion(), null,
                RentalCommand.Action.HANDOVER, member);
    }

    @Transactional
    public RentalDetailStatusResponse returnRental(
        Long rentalId,
        String idempotencyKey,
        RentalVersionRequest request,
        LoginMember member) {

        if (request == null) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return execute(rentalId, idempotencyKey, request.expectedVersion(), null,
                RentalCommand.Action.RETURN, member);
    }

    private RentalDetailStatusResponse execute(Long rentalId, String key, Long expectedVersion, String reason,
                                                RentalCommand.Action action, LoginMember login) {
        if (login == null || login.memberId() == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        if (rentalId == null || rentalId <= 0 || expectedVersion == null || expectedVersion < 0
                || key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || ((action == RentalCommand.Action.CANCEL || action == RentalCommand.Action.REJECT)
                    && (reason == null || reason.isBlank() || reason.length() > 500))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        long actorId = login.memberId();
        key = key.toLowerCase(Locale.ROOT);
        var participants = rentals.findParticipants(rentalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!participants.getOwnerId().equals(actorId) && !participants.getBorrowerId().equals(actorId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        lockParticipants(actorId, participants.getOwnerId(), participants.getBorrowerId());
        var command = new RentalCommand(action, rentalId, expectedVersion, reason);
        Instant now = clock.instant();
        var replay = effects.findCommandReplay(actorId, key, command, now);
        if (replay.isPresent()) {
            rentals.findById(rentalId).filter(r -> r.isParticipant(actorId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            return replay.get();
        }

        // 양측 회원 ID순 → 물건 → 거래 잠금. 잠금 대기 후 거래의 최신 상태 확인.
        long itemId = participants.getItemId();
        Item item = items.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Rental rental = rentals.findByIdForUpdate(rentalId)
                .filter(r -> r.isParticipant(actorId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean owner = rental.getOwnerId().equals(actorId);
        if ((action != RentalCommand.Action.CANCEL && !owner)
                || (action == RentalCommand.Action.CANCEL && rental.getStatus() == RentalStatus.REQUESTED && owner)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (rental.getVersion() != expectedVersion) throw new VersionConflictException(rental.getVersion());
        // 잠금 대기 중 자정·만료 경계를 지날 수 있으므로 변경 직전에 시각 재확인.
        now = clock.instant();
        RentalStatus previous = rental.getStatus();
        switch (action) {
            case APPROVE -> {
                if (previous != RentalStatus.REQUESTED || !rental.getRequestExpiresAt().isAfter(now)) {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
                }
                validateDates(rental.getStartDate(), rental.getEndDate(), LocalDate.ofInstant(now, KST));
                policy.requireApprovalAllowed(item, rental);
                policy.requireAvailable(item);
                if (item.isDeleted() || item.getVisibility() != ItemVisibility.PUBLIC) {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
                }
                if (rental.getStartDate().isBefore(item.getAvailableStartDate())
                        || rental.getEndDate().isAfter(item.getAvailableEndDate())) {
                    throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
                if (rentals.findByItemIdAndStatusOrderById(itemId, RentalStatus.ACTIVE).stream()
                        .anyMatch(r -> r.isOverdue(clock.instant()))) {
                    throw new BusinessException(ErrorCode.ITEM_OVERDUE);
                }
                var occupied = rentals.findOccupiedForUpdate(itemId, OCCUPYING_STATUSES,
                        rental.getStartDate(), rental.getEndDate());
                if (!occupied.isEmpty()) {
                    throw new RentalDateConflictException(occupied.stream().map(Rental::getId).toList());
                }
                rental.approve(now);
            }
            case REJECT -> rental.reject(reason);
            case CANCEL -> rental.cancel(reason);
            case HANDOVER -> {
                if (previous != RentalStatus.APPROVED) throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
                if (!rentals.findByItemIdAndStatusOrderById(itemId, RentalStatus.ACTIVE).isEmpty()) {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
                }
                rental.handover(now);
            }
            case RETURN -> rental.returnItem(now, rentals.findConfirmedReturnProposal(rentalId)
                    .map(AppointmentProposal::getScheduledAt).orElse(null));
        }
        // flush 후 증가한 거래 버전으로 이력 기록.
        rentals.flush();
        histories.saveAndFlush(RentalStatusHistory.changed(rental, previous, actorId));
        RentalDetailStatusResponse response = queries.detail(rental, actorId, now);
        effects.recordCommandSuccess(actorId, key, command, rental, response, now);
        return response;
    }

    /** 상태 변경의 외래 키 검사와 상대 회원의 명령이 역순으로 잠기는 교착 방지. */
    private Member lockParticipants(long actorId, Long... relatedIds) {
        var memberIds = new TreeSet<Long>();
        memberIds.add(actorId);
        for (Long id : relatedIds) {
            if (id != null) memberIds.add(id);
        }
        Member actor = null;
        for (Long id : memberIds) {
            Member locked = members.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(
                    id == actorId ? ErrorCode.UNAUTHENTICATED : ErrorCode.RESOURCE_NOT_FOUND));
            if (id == actorId) actor = locked;
        }
        if (actor == null || actor.getStatus() != MemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }
}
