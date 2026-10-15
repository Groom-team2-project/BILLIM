package com.billim.domain.rental.service;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.entity.*;
import com.billim.domain.rental.repository.*;
import com.billim.global.exception.BusinessException;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.time.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentServiceTest {
    private final AppointmentSlotRepository slots = mock(AppointmentSlotRepository.class);
    private final AppointmentProposalRepository proposals = mock(AppointmentProposalRepository.class);
    private final RentalRepository rentals = mock(RentalRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
    private final AppointmentService service = new AppointmentService(slots, proposals, rentals, members,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final LoginMember login = new LoginMember(2L, MemberRole.USER);

    @BeforeEach
    void setUp() {
        Member member = mock(Member.class);
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        when(members.findById(2L)).thenReturn(Optional.of(member));
        Rental rental = mock(Rental.class);
        when(rental.getId()).thenReturn(10L);
        when(rental.isParticipant(2L)).thenReturn(true);
        when(rental.getStatus()).thenReturn(RentalStatus.APPROVED);
        when(rental.getStartDate()).thenReturn(LocalDate.parse("2026-10-15"));
        when(rentals.findById(10L)).thenReturn(Optional.of(rental));
    }

    @Test
    @DisplayName("제안 없는 슬롯도 응답 목록에 포함")
    void includesSlotWithoutProposal() {
        AppointmentSlot slot = mock(AppointmentSlot.class);
        when(slot.getId()).thenReturn(20L);
        when(slot.getKind()).thenReturn(AppointmentKind.PICKUP);
        when(slot.getVersion()).thenReturn(3L);
        when(slot.getConfirmedProposalId()).thenReturn(null);
        when(slots.findByRentalId(10L)).thenReturn(List.of(slot));
        when(proposals.findBySlotIdAndStatus(20L, AppointmentProposalStatus.PROPOSED))
                .thenReturn(List.of());

        assertThat(service.getAppointments(10L, login).items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("20");
            assertThat(item.rentalId()).isEqualTo("10");
            assertThat(item.version()).isEqualTo(3L);
            assertThat(item.confirmedProposal()).isNull();
            assertThat(item.pendingProposal()).isNull();
            assertThat(item.allowedActions()).containsExactly("PROPOSE");
        });
    }

    @Test
    @DisplayName("거래 당사자가 아니면 슬롯 조회 전 거부")
    void rejectsNonParticipant() {
        when(rentals.findById(10L).orElseThrow().isParticipant(2L)).thenReturn(false);
        assertThatThrownBy(() -> service.getAppointments(10L, login))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(slots, proposals);
    }

    @Test
    @DisplayName("슬롯 없는 대여는 빈 약속 목록 반환")
    void returnsEmptySlots() {
        when(slots.findByRentalId(10L)).thenReturn(List.of());
        assertThat(service.getAppointments(10L, login).items()).isEmpty();
        verifyNoInteractions(proposals);
    }

    @Test
    @DisplayName("로그인 정보가 없으면 조회 거부")
    void rejectsAnonymous() {
        assertThatThrownBy(() -> service.getAppointments(10L, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(slots, proposals);
    }

    @Test
    @DisplayName("대기 제안 중복은 데이터 정합성 오류 처리")
    void rejectsDuplicatePendingProposals() {
        AppointmentSlot slot = mock(AppointmentSlot.class);
        when(slot.getId()).thenReturn(20L);
        when(slot.getConfirmedProposalId()).thenReturn(null);
        when(slots.findByRentalId(10L)).thenReturn(List.of(slot));
        when(proposals.findBySlotIdAndStatus(20L, AppointmentProposalStatus.PROPOSED))
                .thenReturn(List.of(mock(AppointmentProposal.class), mock(AppointmentProposal.class)));
        assertThatThrownBy(() -> service.getAppointments(10L, login))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("상대방의 미래 제안은 수락 가능하고 과거 제안은 수락 불가")
    void acceptsOnlyFutureCounterpartyProposal() {
        AppointmentSlot slot = mock(AppointmentSlot.class);
        when(slot.getId()).thenReturn(20L);
        when(slot.getKind()).thenReturn(AppointmentKind.PICKUP);
        when(slot.getConfirmedProposalId()).thenReturn(null);
        when(slots.findByRentalId(10L)).thenReturn(List.of(slot));
        AppointmentProposal proposal = mock(AppointmentProposal.class);
        when(proposal.getId()).thenReturn(30L);
        when(proposal.getSlotId()).thenReturn(20L);
        when(proposal.getProposerId()).thenReturn(1L);
        when(proposal.getPlaceId()).thenReturn(3L);
        when(proposal.getStatus()).thenReturn(AppointmentProposalStatus.PROPOSED);
        when(proposal.getScheduledAt()).thenReturn(NOW.plusSeconds(3600));
        when(proposals.findBySlotIdAndStatus(20L, AppointmentProposalStatus.PROPOSED))
                .thenReturn(List.of(proposal));
        assertThat(service.getAppointments(10L, login).items().getFirst().allowedActions())
                .containsExactly("PROPOSE", "ACCEPT");
        when(proposal.getScheduledAt()).thenReturn(NOW);
        assertThat(service.getAppointments(10L, login).items().getFirst().allowedActions())
                .containsExactly("PROPOSE");
        when(proposal.getProposerId()).thenReturn(2L);
        assertThat(service.getAppointments(10L, login).items().getFirst().allowedActions())
                .containsExactly("PROPOSE", "WITHDRAW");
    }

    @Test
    @DisplayName("반납 완료 거래는 새 약속 제안 불가")
    void disablesProposalAfterReturn() {
        when(rentals.findById(10L).orElseThrow().getStatus()).thenReturn(RentalStatus.RETURNED);
        AppointmentSlot slot = mock(AppointmentSlot.class);
        when(slot.getId()).thenReturn(20L);
        when(slot.getKind()).thenReturn(AppointmentKind.PICKUP);
        when(slot.getConfirmedProposalId()).thenReturn(null);
        when(slots.findByRentalId(10L)).thenReturn(List.of(slot));
        when(proposals.findBySlotIdAndStatus(20L, AppointmentProposalStatus.PROPOSED)).thenReturn(List.of());
        assertThat(service.getAppointments(10L, login).items().getFirst().allowedActions()).isEmpty();
    }
}
