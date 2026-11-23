package com.billim.domain.rental.service;

import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.item.dto.PlaceResponse;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.dto.AppointmentSlotResponse;
import com.billim.domain.rental.dto.AppointmentSlotsResponse;
import com.billim.domain.rental.dto.RentalDetailStatusResponse;
import com.billim.domain.rental.entity.AppointmentProposal;
import com.billim.domain.rental.entity.AppointmentProposalStatus;
import com.billim.domain.rental.entity.AppointmentSlot;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.entity.AppointmentKind;
import com.billim.domain.rental.repository.AppointmentProposalRepository;
import com.billim.domain.rental.repository.AppointmentSlotRepository;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {
    //P1 약속 제안·수락·철회·조회
    private final AppointmentSlotRepository appointmentSlotRepository;
    private final AppointmentProposalRepository appointmentProposalRepository;
    private final RentalRepository rentalRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final CommunityPlaceRepository places;

    public AppointmentSlotsResponse getAppointments(Long rentalId, LoginMember member) {
        Rental rental = requireRental(rentalId, member);
        Long memberId = member.memberId();

        //약속 슬롯 조회
        List<AppointmentSlot> slots = appointmentSlotRepository.findByRentalId(rentalId);

        if (slots.isEmpty()) {
            return new AppointmentSlotsResponse(List.of());
        }

        List<Long> confirmedIds = slots.stream().map(AppointmentSlot::getConfirmedProposalId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, AppointmentProposal> confirmedById = confirmedIds.isEmpty() ? Map.of()
                : appointmentProposalRepository.findAllById(confirmedIds).stream()
                    .collect(Collectors.toMap(AppointmentProposal::getId, Function.identity()));
        Map<Long, List<AppointmentProposal>> pendingBySlot = appointmentProposalRepository
                .findBySlotIdInAndStatus(slots.stream().map(AppointmentSlot::getId).toList(), AppointmentProposalStatus.PROPOSED)
                .stream().collect(Collectors.groupingBy(AppointmentProposal::getSlotId));
        List<Long> placeIds = Stream.concat(confirmedById.values().stream(),
                pendingBySlot.values().stream().flatMap(List::stream))
                .map(AppointmentProposal::getPlaceId).distinct().toList();
        Map<Long, CommunityPlace> placesById = placeIds.isEmpty() ? Map.of()
                : places.findByCommunityIdAndIdIn(rental.getCommunityId(), placeIds).stream()
                    .collect(Collectors.toMap(CommunityPlace::getId, Function.identity()));
        Instant now = clock.instant();
        List<AppointmentSlotResponse> items = new ArrayList<>();
        for (AppointmentSlot slot : slots) {
            items.add(toSlotResponse(rental, slot,
                    slot.getConfirmedProposalId() == null ? null : confirmedById.get(slot.getConfirmedProposalId()),
                    pendingBySlot.getOrDefault(slot.getId(), List.of()), memberId, now,
                    proposal -> placesById.get(proposal.getPlaceId())));
        }
        return new AppointmentSlotsResponse(items);
    }

    public AppointmentSlotResponse getAppointment(Long rentalId, Long slotId, LoginMember member) {
        Rental rental = requireRental(rentalId, member);
        AppointmentSlot slot = appointmentSlotRepository.findById(slotId)
                .filter(value -> value.getRentalId().equals(rentalId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return toSlotResponse(rental, slot, member.memberId(), clock.instant());
    }

    private Rental requireRental(Long rentalId, LoginMember member) {
        //로그인 사용자 확인
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        Long memberId = member.memberId();

        memberRepository.findById(memberId)
            .filter(m-> m.getStatus() == MemberStatus.ACTIVE)
            .orElseThrow(()-> new BusinessException(ErrorCode.UNAUTHENTICATED));

        //RentalRepository로 대여 조회
        if (rentalId == null || rentalId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        //사용자가 소유자 또는 대여자인지 확인
        return rentalRepository.findById(rentalId)
            .filter(r-> r.isParticipant(memberId))
            .orElseThrow(()-> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AppointmentSlotResponse toSlotResponse(Rental rental, AppointmentSlot slot, long memberId, Instant now) {
        AppointmentProposal confirmed = null;
        if (slot.getConfirmedProposalId() != null) {
            confirmed = appointmentProposalRepository.findById(slot.getConfirmedProposalId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        }
        List<AppointmentProposal> pendingProposals = appointmentProposalRepository
                .findBySlotIdAndStatus(slot.getId(), AppointmentProposalStatus.PROPOSED);
        return toSlotResponse(rental, slot, confirmed, pendingProposals, memberId, now,
                proposal -> places.findByIdAndCommunityId(proposal.getPlaceId(), rental.getCommunityId()).orElse(null));
    }

    private AppointmentSlotResponse toSlotResponse(Rental rental, AppointmentSlot slot, AppointmentProposal confirmed,
            List<AppointmentProposal> pendingProposals, long memberId, Instant now,
            Function<AppointmentProposal, CommunityPlace> placeLookup) {
        if (slot.getConfirmedProposalId() != null && confirmed == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (pendingProposals.size() > 1) {
            throw new IllegalStateException("하나의 약속 슬롯에 대기 제안이 여러 개 존재");
        }
        AppointmentProposal pending = pendingProposals.isEmpty() ? null : pendingProposals.getFirst();
        if (confirmed != null && (!slot.getId().equals(confirmed.getSlotId())
                || confirmed.getStatus() != AppointmentProposalStatus.CONFIRMED)) {
            throw new IllegalStateException("약속 슬롯의 확정 제안 참조 불일치");
        }
        return new AppointmentSlotResponse(
                slot.getId().toString(), rental.getId().toString(),
                slot.getKind(), slot.getVersion(),
                toProposalResponse(confirmed, slot, rental, placeLookup),
                toProposalResponse(pending, slot, rental, placeLookup),
                allowedActions(rental, slot, pending, memberId, now)
        );
    }

    private List<String> allowedActions(Rental rental, AppointmentSlot slot,
                                       AppointmentProposal pending, long memberId, Instant now) {
        List<String> actions = new ArrayList<>();
        boolean editable = rental.getStatus() == RentalStatus.APPROVED
                || (slot.getKind() == AppointmentKind.RETURN && rental.getStatus() == RentalStatus.ACTIVE);
        LocalDate date = slot.getKind() == AppointmentKind.PICKUP
                ? rental.getStartDate() : rental.getEndDate();
        ZoneId kst = ZoneId.of("Asia/Seoul");
        if (editable && date != null && date.plusDays(1).atStartOfDay(kst).toInstant().isAfter(now)) {
            actions.add("PROPOSE");
            if (pending != null && !pending.getProposerId().equals(memberId)
                    && pending.getScheduledAt().isAfter(now)
                    && LocalDate.ofInstant(pending.getScheduledAt(), kst).equals(date)) {
                actions.add("ACCEPT");
            }
        }
        if (pending != null && pending.getProposerId().equals(memberId)) {
            actions.add("WITHDRAW");
        }
        return List.copyOf(actions);
    }

    private RentalDetailStatusResponse.AppointmentProposal toProposalResponse(
        AppointmentProposal proposal,
        AppointmentSlot slot,
        Rental rental,
        Function<AppointmentProposal, CommunityPlace> placeLookup
    ) {
        if (proposal == null) {
            return null;
        }
        var place = placeLookup.apply(proposal);
        return new RentalDetailStatusResponse.AppointmentProposal(
            proposal.getId().toString(),
            proposal.getSlotId().toString(),
            proposal.getProposerId().toString(),
            slot.getKind().name(),
            proposal.getScheduledAt(),
            new PlaceResponse(
                proposal.getPlaceId().toString(),
                rental.getCommunityId().toString(),
                proposal.getPlaceNameSnapshot(),
                place == null ? null : place.getLatitude(),
                place == null ? null : place.getLongitude(),
                place == null ? null : place.getGuide()
            ),
            proposal.getStatus().name(),
            proposal.getAcceptedBy() == null
                ? null
                : proposal.getAcceptedBy().toString(),
            proposal.getAcceptedAt()
        );
    }
}
