package com.billim.domain.rental.service;

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

    public AppointmentSlotsResponse getAppointments(Long rentalId, LoginMember member) {
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
        Rental rental = rentalRepository.findById(rentalId)
            .filter(r-> r.isParticipant(memberId))
            .orElseThrow(()-> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        //약속 슬롯 조회
        List<AppointmentSlot> slots = appointmentSlotRepository.findByRentalId(rentalId);

        if (slots.isEmpty()) {
            return new AppointmentSlotsResponse(List.of());
        }

        List<AppointmentSlotResponse> items = new ArrayList<>();
        Instant now = clock.instant();

        //슬롯과 확정 대기 제안 조회
        for (AppointmentSlot slot : slots) {
            AppointmentProposal confirmed = null;
            if(slot.getConfirmedProposalId() != null) {
                confirmed = appointmentProposalRepository
                    .findById(slot.getConfirmedProposalId())
                    .orElseThrow(()-> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            }
            List<AppointmentProposal> pendingProposals =
                appointmentProposalRepository.findBySlotIdAndStatus(slot.getId(), AppointmentProposalStatus.PROPOSED);
            if (pendingProposals.size() > 1) {
                throw new IllegalStateException("하나의 약속 슬롯에 대기 제안이 여러 개 존재");
            }
            AppointmentProposal pending = pendingProposals.isEmpty() ? null : pendingProposals.getFirst();
            if (confirmed != null && (!slot.getId().equals(confirmed.getSlotId())
                    || confirmed.getStatus() != AppointmentProposalStatus.CONFIRMED)) {
                throw new IllegalStateException("약속 슬롯의 확정 제안 참조 불일치");
            }
            items.add(new AppointmentSlotResponse(
                    slot.getId().toString(), rental.getId().toString(),
                    slot.getKind(), slot.getVersion(),
                    toProposalResponse(confirmed, slot, rental),
                    toProposalResponse(pending, slot, rental),
                    allowedActions(rental, slot, pending, memberId, now)
            ));
        }

        //AppointmentSlotResponse로 변환해 반환
        return new AppointmentSlotsResponse(items);
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
        Rental rental
    ) {
        if (proposal == null) {
            return null;
        }
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
                // 공용 장소 도메인 미구현으로 좌표·안내 생략
                null,
                null,
                null
            ),
            proposal.getStatus().name(),
            proposal.getAcceptedBy() == null
                ? null
                : proposal.getAcceptedBy().toString(),
            proposal.getAcceptedAt()
        );
    }
}
