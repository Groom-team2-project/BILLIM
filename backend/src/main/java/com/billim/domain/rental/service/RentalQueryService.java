package com.billim.domain.rental.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.rental.repository.RentalStatusHistoryRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RentalQueryService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<RentalStatus> OCCUPYING_STATUSES =
            List.of(RentalStatus.APPROVED, RentalStatus.ACTIVE, RentalStatus.RETURNED);
    private final RentalRepository rentals;
    private final RentalStatusHistoryRepository histories;
    private final MemberRepository members;
    private final ItemRepository items;
    private final RentalRequestPolicy policy;
    private final Clock clock;

    public RentalQueryService(RentalRepository rentals, RentalStatusHistoryRepository histories,
                              MemberRepository members, ItemRepository items,
                              RentalRequestPolicy policy, Clock clock) {
        this.rentals = rentals;
        this.histories = histories;
        this.members = members;
        this.items = items;
        this.policy = policy;
        this.clock = clock;
    }

    public RentalListResponse getRentalList(RentalRole role, RentalStatus status, boolean overdueOnly,
                                            Long itemId, int page, int size, LoginMember member) {
        long memberId = requireMember(member);
        if (role == null || page < 0 || size < 1 || size > 50 || (itemId != null && itemId <= 0)
                || (overdueOnly && status != null && status != RentalStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.instant();
        Specification<Rental> filter = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get(role == RentalRole.OWNER ? "ownerId" : "borrowerId"), memberId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (itemId != null) predicates.add(cb.equal(root.get("itemId"), itemId));
            if (overdueOnly) {
                predicates.add(cb.equal(root.get("status"), RentalStatus.ACTIVE));
                predicates.add(cb.lessThanOrEqualTo(root.get("dueAt"), now));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = rentals.findAll(filter, PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new RentalListResponse(result.getContent().stream().map(r -> summary(r, memberId, now)).toList(),
                page, size, result.getTotalElements(), result.hasNext());
    }

    public RentalCountStatusResponse getRentalCountStatus(LoginMember member) {
        long memberId = requireMember(member);
        Instant now = clock.instant();
        var next = rentals.findNextRentals(memberId, LocalDate.ofInstant(now, KST), PageRequest.of(0, 3));
        return new RentalCountStatusResponse(counts(memberId, false, now), counts(memberId, true, now),
                next.stream().map(r -> summary(r, memberId, now)).toList());
    }

    public RentalDetailStatusResponse getRentalDetailStatus(Long rentalId, LoginMember member) {
        long memberId = requireMember(member);
        if (rentalId == null || rentalId <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        Rental rental = rentals.findById(rentalId)
                .filter(r -> r.isParticipant(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return detail(rental, memberId, clock.instant());
    }

    private RentalDetailStatusResponse detail(Rental rental, long memberId, Instant now) {
        var entries = histories.findByRentalIdOrderByRentalVersionAsc(rental.getId());
        var history = entries.stream()
                .map(h -> new RentalDetailStatusResponse.History(h.getFromStatus(), h.getToStatus(),
                        h.getActorId() == null ? null : h.getActorId().toString(), h.getReason(),
                        h.getRentalVersion(), h.getCreatedAt())).toList();
        return new RentalDetailStatusResponse(summary(rental, memberId, now), rental.getReason(),
                rental.getRequestExpiresAt(), rental.getDueAt(), rental.getApprovedAt(),
                rental.getHandedOverAt(), rental.getReturnedAt(),
                rental.getChatRoomId() == null ? null : rental.getChatRoomId().toString(), history, List.of());
    }

    public RentalAvailabilityResponse getAvailability(Long itemId, LocalDate from, LocalDate to,
                                                       LocalDate startDate, LocalDate endDate, LoginMember member) {
        long memberId = requireMember(member);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KST);
        if (itemId == null || itemId <= 0 || from == null || to == null || from.isAfter(to)
                || ChronoUnit.DAYS.between(from, to) >= 93
                || (startDate == null) != (endDate == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (startDate != null && (startDate.isBefore(today) || startDate.isAfter(endDate)
                || ChronoUnit.DAYS.between(startDate, endDate) >= 30)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Item item = items.findById(itemId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        policy.requireViewable(item, memberId);
        var occupied = rentals.findOccupied(itemId, OCCUPYING_STATUSES, from, to);
        var ranges = new ArrayList<RentalAvailabilityResponse.DateRange>();
        for (Rental rental : occupied) {
            LocalDate start = rental.getStartDate().isBefore(from) ? from : rental.getStartDate();
            LocalDate end = rental.getEndDate().isAfter(to) ? to : rental.getEndDate();
            if (!ranges.isEmpty()) {
                var last = ranges.getLast();
                if (ChronoUnit.DAYS.between(last.endDate(), start) <= 1) {
                    ranges.set(ranges.size() - 1, new RentalAvailabilityResponse.DateRange(last.startDate(),
                            end.isAfter(last.endDate()) ? end : last.endDate()));
                    continue;
                }
            }
            ranges.add(new RentalAvailabilityResponse.DateRange(start, end));
        }
        boolean overdue = rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(itemId, RentalStatus.ACTIVE, now);
        Boolean selectedAvailable = startDate == null ? null : !overdue
                && !startDate.isBefore(item.getAvailableStartDate()) && !endDate.isAfter(item.getAvailableEndDate())
                && !rentals.hasOccupiedDates(itemId, OCCUPYING_STATUSES, startDate, endDate);
        return new RentalAvailabilityResponse(itemId.toString(), item.getAvailableStartDate(),
                item.getAvailableEndDate(), List.copyOf(ranges), overdue, now, selectedAvailable);
    }

    private long requireMember(LoginMember login) {
        if (login == null || login.memberId() == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        members.findById(login.memberId()).filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        return login.memberId();
    }

    private RentalSummaryResponse summary(Rental rental, long memberId, Instant now) {
        Member owner = members.findById(rental.getOwnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Member borrower = members.findById(rental.getBorrowerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        LocalDate today = LocalDate.ofInstant(now, KST);
        LocalDate targetDate = rental.getStatus() == RentalStatus.ACTIVE ? rental.getEndDate() : rental.getStartDate();
        return new RentalSummaryResponse(rental.getId().toString(), rental.getItemId().toString(),
                rental.getItemTitleSnapshot(), MemberSummaryResponse.from(owner), MemberSummaryResponse.from(borrower),
                rental.getStartDate(), rental.getEndDate(), rental.getStatus(), rental.isOverdue(now),
                Math.toIntExact(ChronoUnit.DAYS.between(today, targetDate)), rental.getPlaceNameSnapshot(),
                rental.getVersion(), allowedActions(rental, memberId, today), rental.getCreatedAt());
    }

    private List<String> allowedActions(Rental rental, long memberId, LocalDate today) {
        boolean owner = rental.getOwnerId().equals(memberId);
        return switch (rental.getStatus()) {
            // 승인 정책 연동 전에는 승인 가능 여부를 단정하지 않음.
            case REQUESTED -> owner ? List.of("REJECT") : List.of("CANCEL");
            case APPROVED -> owner && !today.isBefore(rental.getStartDate()) && !today.isAfter(rental.getEndDate())
                    && !rentals.existsByItemIdAndStatus(rental.getItemId(), RentalStatus.ACTIVE)
                    ? List.of("CANCEL", "HANDOVER") : List.of("CANCEL");
            case ACTIVE -> owner ? List.of("RETURN") : List.of();
            default -> List.of();
        };
    }

    private RentalCountStatusResponse.RentalCounts counts(long memberId, boolean owner, Instant now) {
        var totals = new EnumMap<RentalStatus, Integer>(RentalStatus.class);
        rentals.countStatuses(memberId, owner).forEach(row -> totals.put(row.getStatus(), Math.toIntExact(row.getTotal())));
        return new RentalCountStatusResponse.RentalCounts(totals.getOrDefault(RentalStatus.REQUESTED, 0),
                totals.getOrDefault(RentalStatus.APPROVED, 0), totals.getOrDefault(RentalStatus.ACTIVE, 0),
                Math.toIntExact(rentals.countOverdue(memberId, owner, now)),
                totals.getOrDefault(RentalStatus.RETURNED, 0), totals.getOrDefault(RentalStatus.REJECTED, 0),
                totals.getOrDefault(RentalStatus.CANCELED, 0), totals.getOrDefault(RentalStatus.EXPIRED, 0));
    }
}
