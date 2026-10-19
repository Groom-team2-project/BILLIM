package com.billim.domain.rental.service;

import org.springframework.transaction.annotation.Isolation;
import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.service.*;
import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.*;
import com.billim.domain.rental.repository.*;
import com.billim.global.exception.*;
import com.billim.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AppointmentCommandService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final ItemRepository items;
    private final RentalRepository rentals;
    private final AppointmentSlotRepository slots;
    private final AppointmentProposalRepository proposals;
    private final CommunityPlaceRepository places;
    private final MemberRepository members;
    private final AppointmentService queries;
    private final DomainEventPublisher events;
    private final Clock clock;
    private final RentalRequestEffects effects;

    public AppointmentSlotResponse propose(Long rentalId, AppointmentKind kind,
                                           ProposeAppointmentRequest request, String key, LoginMember login) {
        key = normalizeKey(key);
        long memberId = requireMember(login);
        Rental rental = lockRental(rentalId, memberId);
        String operation = "POST /api/v1/rentals/" + rentalId + "/appointments/" + kind + "/proposals";
        var replay = effects.findAppointmentReplay(memberId, operation, key, request, 201, clock.instant());
        if (replay.isPresent()) return replay.get();
        AppointmentSlot slot = slots.findByRentalIdAndKind(rentalId, kind)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        checkVersion(slot, request.expectedVersion());
        validateSchedule(rental, slot, request.scheduledAt());
        var place = requirePlace(Long.parseLong(request.placeId()), rental);
        proposals.findBySlotIdAndStatus(slot.getId(), AppointmentProposalStatus.PROPOSED)
                .forEach(AppointmentProposal::supersede);
        AppointmentProposal proposal = proposals.saveAndFlush(AppointmentProposal.propose(slot.getId(), memberId,
                place.getId(), request.scheduledAt(), place.getName()));
        var response = complete(rental, slot, proposal, memberId, login, EventType.APPOINTMENT_PROPOSED);
        effects.recordAppointmentSuccess(memberId, operation, key, request, 201, response, clock.instant());
        return response;
    }

    public AppointmentSlotResponse accept(Long proposalId, AppointmentVersionRequest request, String key, LoginMember login) {
        return change(proposalId, request, key, login, true);
    }

    public AppointmentSlotResponse withdraw(Long proposalId, AppointmentVersionRequest request, String key, LoginMember login) {
        return change(proposalId, request, key, login, false);
    }

    private AppointmentSlotResponse change(Long proposalId, AppointmentVersionRequest request, String key,
                                            LoginMember login, boolean accept) {
        key = normalizeKey(key);
        long memberId = requireMember(login);
        // 잠금 전에는 불변 참조만 조회하고 상태는 거래 잠금 뒤 조회
        Long slotId = proposals.findSlotId(proposalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Long rentalId = slots.findRentalId(slotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Rental rental = lockRental(rentalId, memberId);
        String operation = "POST /api/v1/appointments/" + proposalId + (accept ? "/accept" : "/withdraw");
        var replay = effects.findAppointmentReplay(memberId, operation, key, request, 200, clock.instant());
        if (replay.isPresent()) return replay.get();
        AppointmentSlot slot = slots.findByIdForUpdate(slotId).orElseThrow();
        AppointmentProposal proposal = proposals.findByIdForUpdate(proposalId).orElseThrow();
        checkVersion(slot, request.expectedVersion());
        if (proposal.getStatus() != AppointmentProposalStatus.PROPOSED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        if (accept == proposal.getProposerId().equals(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (accept) {
            validateSchedule(rental, slot, proposal.getScheduledAt());
            requirePlace(proposal.getPlaceId(), rental);
            if (slot.getConfirmedProposalId() != null) {
                AppointmentProposal previous = proposals.findByIdForUpdate(slot.getConfirmedProposalId()).orElseThrow();
                if (!previous.getSlotId().equals(slotId)
                        || previous.getStatus() != AppointmentProposalStatus.CONFIRMED) {
                    throw new IllegalStateException("확정 약속 참조 불일치");
                }
                previous.supersede();
            }
            proposal.confirm(memberId, clock.instant());
            slot.confirm(proposalId);
        } else {
            proposal.withdraw();
        }
        var response = complete(rental, slot, proposal, memberId, login,
                accept ? EventType.APPOINTMENT_CONFIRMED : EventType.APPOINTMENT_WITHDRAWN);
        effects.recordAppointmentSuccess(memberId, operation, key, request, 200, response, clock.instant());
        return response;
    }

    private AppointmentSlotResponse complete(Rental rental, AppointmentSlot slot, AppointmentProposal proposal,
                                              long memberId, LoginMember login, EventType type) {
        slot.changed(clock.instant());
        slots.flush();
        boolean owner = rental.getOwnerId().equals(memberId);
        var recipient = EventPayload.Recipient.of(owner ? rental.getBorrowerId() : rental.getOwnerId(),
                owner ? RecipientRole.BORROWER : RecipientRole.OWNER);
        var actor = members.findById(memberId).orElseThrow();
        events.publish(DomainEvent.of(type, slot.getId(), slot.getVersion(),
                EventPayload.forRental(rental.getId(), List.of(recipient), EventPayload.Display.ofAppointment(
                        actor.getDisplayName(), rental.getItemTitleSnapshot(),
                        proposal.getPlaceNameSnapshot(), proposal.getScheduledAt()))));
        return queries.getAppointments(rental.getId(), login).items().stream()
                .filter(response -> response.id().equals(slot.getId().toString())).findFirst().orElseThrow();
    }

    private String normalizeKey(String key) {
        if (key == null || !key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return key.toLowerCase(java.util.Locale.ROOT);
    }

    private long requireMember(LoginMember login) {
        if (login == null || login.memberId() == null) throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        return login.memberId();
    }

    private Rental lockRental(Long rentalId, long memberId) {
        var participants = rentals.findParticipants(rentalId)
                .filter(p -> p.getOwnerId().equals(memberId) || p.getBorrowerId().equals(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        java.util.stream.Stream.of(participants.getOwnerId(), participants.getBorrowerId()).distinct().sorted()
                .forEach(id -> {
                    var member = members.findByIdForUpdate(id)
                            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
                    if (id.equals(memberId) && member.getStatus() != MemberStatus.ACTIVE) {
                        throw new BusinessException(ErrorCode.UNAUTHENTICATED);
                    }
                });
        items.findByIdForUpdate(participants.getItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        members.findById(memberId).filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        return rentals.findByIdForUpdate(rentalId).filter(r -> r.isParticipant(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void checkVersion(AppointmentSlot slot, Long expected) {
        if (expected == null || expected < 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (slot.getVersion() != expected) throw new VersionConflictException(slot.getVersion());
    }

    private void validateSchedule(Rental rental, AppointmentSlot slot, Instant scheduledAt) {
        boolean editable = rental.getStatus() == RentalStatus.APPROVED
                || (slot.getKind() == AppointmentKind.RETURN && rental.getStatus() == RentalStatus.ACTIVE);
        if (!editable) throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        LocalDate date = slot.getKind() == AppointmentKind.PICKUP ? rental.getStartDate() : rental.getEndDate();
        if (scheduledAt == null || !scheduledAt.isAfter(clock.instant())
                || !LocalDate.ofInstant(scheduledAt, KST).equals(date)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private CommunityPlace requirePlace(long placeId, Rental rental) {
        return places.findByIdAndCommunityId(placeId, rental.getCommunityId())
                .filter(CommunityPlace::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
    }
}
