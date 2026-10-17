package com.billim.domain.rental.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.*;
import com.billim.domain.rental.repository.*;
import com.billim.global.exception.*;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RentalCommandServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";
    private RentalRepository rentals;
    private RentalStatusHistoryRepository histories;
    private ItemRepository items;
    private MemberRepository members;
    private RentalRequestPolicy policy;
    private RentalRequestEffects effects;
    private RentalQueryService queries;
    private RentalService service;
    private Rental rental;
    private Item item;

    @BeforeEach
    void setUp() {
        rentals = mock(RentalRepository.class);
        histories = mock(RentalStatusHistoryRepository.class);
        items = mock(ItemRepository.class);
        members = mock(MemberRepository.class);
        policy = mock(RentalRequestPolicy.class);
        effects = mock(RentalRequestEffects.class);
        queries = mock(RentalQueryService.class);
        service = new RentalService(rentals, histories, items, members, policy, effects,
                Clock.fixed(NOW, ZoneOffset.UTC), queries, mock(com.billim.domain.rental.repository.AppointmentSlotRepository.class));
        for (long id = 1; id <= 3; id++) {
            Member member = Member.register("회원");
            ReflectionTestUtils.setField(member, "id", id);
            when(members.findByIdForUpdate(id)).thenReturn(Optional.of(member));
        }
        item = Item.create(1L, 1L, 1L, 1L, "드릴", null,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-11-30"), NOW);
        ReflectionTestUtils.setField(item, "id", 10L);
        rental = Rental.request(10L, 1L, 2L, 1L, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-11"),
                NOW.plusSeconds(86400), NOW.plusSeconds(172800), "드릴", 1L, "정문");
        ReflectionTestUtils.setField(rental, "id", 100L);
        RentalRepository.Participants participants = mock(RentalRepository.Participants.class);
        when(participants.getItemId()).thenReturn(10L);
        when(participants.getOwnerId()).thenReturn(1L);
        when(participants.getBorrowerId()).thenReturn(2L);
        when(rentals.findParticipants(100L)).thenReturn(Optional.of(participants));
        when(items.findByIdForUpdate(10L)).thenReturn(Optional.of(item));
        when(rentals.findByIdForUpdate(100L)).thenReturn(Optional.of(rental));
        when(rentals.findById(100L)).thenReturn(Optional.of(rental));
        doAnswer(invocation -> { ReflectionTestUtils.setField(rental, "version", rental.getVersion() + 1); return null; })
                .when(rentals).flush();
    }

    @ParameterizedTest
    @EnumSource(RentalCommand.Action.class)
    @DisplayName("상태 변경·서버 시각·변경 후 버전 이력·후속 기록")
    void transitionsAndRecordsHistory(RentalCommand.Action action) {
        prepare(action);
        RentalStatus before = rental.getStatus();
        call(action, action == RentalCommand.Action.CANCEL ? 2 : 1, 0);
        RentalStatus expected = switch (action) {
            case APPROVE -> RentalStatus.APPROVED;
            case REJECT -> RentalStatus.REJECTED;
            case CANCEL -> RentalStatus.CANCELED;
            case HANDOVER -> RentalStatus.ACTIVE;
            case RETURN -> RentalStatus.RETURNED;
        };
        assertThat(rental.getStatus()).isEqualTo(expected);
        verify(histories).saveAndFlush(argThat(h -> h.getFromStatus() == before
                && h.getToStatus() == expected && h.getRentalVersion() == 1));
        verify(effects).recordCommandSuccess(anyLong(), eq(KEY), argThat(c -> c.action() == action), eq(rental), any(), eq(NOW));
        switch (action) {
            case APPROVE -> assertThat(rental.getApprovedAt()).isEqualTo(NOW);
            case HANDOVER -> assertThat(rental.getHandedOverAt()).isEqualTo(NOW);
            case RETURN -> {
                assertThat(rental.getReturnedAt()).isEqualTo(NOW);
                assertThat(rental.getReturnPromiseKept()).isNull();
            }
            default -> assertThat(rental.getReason()).isEqualTo("일정 변경");
        }
    }

    @Test
    @DisplayName("회원·물건·거래 순서로 잠금 후 승인")
    void locksInConsistentOrder() {
        call(RentalCommand.Action.APPROVE, 1, 0);
        var order = inOrder(members, items, rentals);
        order.verify(members).findByIdForUpdate(1L);
        order.verify(members).findByIdForUpdate(2L);
        order.verify(items).findByIdForUpdate(10L);
        order.verify(rentals).findByIdForUpdate(100L);
        order.verify(rentals).findOccupiedForUpdate(eq(10L), any(), any(), any());
        order.verify(rentals).flush();
    }

    @ParameterizedTest
    @EnumSource(value = RentalCommand.Action.class, names = {"APPROVE", "REJECT", "HANDOVER", "RETURN"})
    @DisplayName("요청자는 소유자 전용 명령 실행 불가")
    void borrowerCannotRunOwnerCommands(RentalCommand.Action action) {
        prepare(action);
        error(() -> call(action, 2, 0), ErrorCode.FORBIDDEN);
        verify(rentals, never()).flush();
    }

    @Test
    @DisplayName("무관한 회원은 404, 소유자는 요청 상태 취소 불가")
    void enforcesParticipantAndCancelRules() {
        error(() -> call(RentalCommand.Action.CANCEL, 3, 0), ErrorCode.RESOURCE_NOT_FOUND);
        error(() -> call(RentalCommand.Action.CANCEL, 1, 0), ErrorCode.FORBIDDEN);
        rental.approve(NOW);
        call(RentalCommand.Action.CANCEL, 1, 0);
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.CANCELED);
    }

    @ParameterizedTest
    @EnumSource(RentalCommand.Action.class)
    @DisplayName("종료 상태 변경 거부")
    void rejectsTerminalState(RentalCommand.Action action) {
        ReflectionTestUtils.setField(rental, "status", RentalStatus.RETURNED);
        error(() -> call(action, 1, 0), ErrorCode.INVALID_STATE_TRANSITION);
        verifyNoInteractions(histories);
    }

    @Test
    @DisplayName("기대한 버전 불일치 시 현재 버전 제공")
    void rejectsStaleVersion() {
        assertThatThrownBy(() -> call(RentalCommand.Action.APPROVE, 1, 3))
                .isInstanceOfSatisfying(VersionConflictException.class, e -> assertThat(e.getCurrentVersion()).isZero());
        verifyNoInteractions(policy, histories);
    }

    @Test
    @DisplayName("승인 시 충돌 거래 식별자 제공 및 상태 보존")
    void approvalConflictRetainsRequested() {
        when(rentals.findOccupiedForUpdate(anyLong(), any(), any(), any())).thenReturn(List.of(rental));
        assertThatThrownBy(() -> call(RentalCommand.Action.APPROVE, 1, 0))
                .isInstanceOfSatisfying(RentalDateConflictException.class,
                        e -> assertThat(e.getConflictingRentalIds()).containsExactly("100"));
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.REQUESTED);
    }

    @Test
    @DisplayName("만료·가능 기간 변경·연체·승인 정책 오류 거부")
    void rejectsInvalidApprovalConditions() {
        ReflectionTestUtils.setField(rental, "requestExpiresAt", NOW);
        error(() -> call(RentalCommand.Action.APPROVE, 1, 0), ErrorCode.INVALID_STATE_TRANSITION);
        ReflectionTestUtils.setField(rental, "requestExpiresAt", NOW.plusSeconds(86400));
        ReflectionTestUtils.setField(item, "availableStartDate", LocalDate.parse("2026-10-11"));
        error(() -> call(RentalCommand.Action.APPROVE, 1, 0), ErrorCode.INVALID_REQUEST);
        ReflectionTestUtils.setField(item, "availableStartDate", LocalDate.parse("2026-10-01"));
        Rental overdue = mock(Rental.class);
        when(overdue.isOverdue(NOW)).thenReturn(true);
        when(rentals.findByItemIdAndStatusOrderById(10L, RentalStatus.ACTIVE)).thenReturn(List.of(overdue));
        error(() -> call(RentalCommand.Action.APPROVE, 1, 0), ErrorCode.ITEM_OVERDUE);
        doThrow(new BusinessException(ErrorCode.MEMBER_RESTRICTED)).when(policy).requireApprovalAllowed(item, rental);
        error(() -> call(RentalCommand.Action.APPROVE, 1, 0), ErrorCode.MEMBER_RESTRICTED);
        verifyNoInteractions(histories);
    }

    @Test
    @DisplayName("전달은 약정 날짜 안이며 다른 진행 거래가 없을 때만 허용")
    void handoverRequiresDatesAndNoActiveRental() {
        rental.approve(NOW);
        ReflectionTestUtils.setField(rental, "startDate", LocalDate.parse("2026-10-11"));
        error(() -> call(RentalCommand.Action.HANDOVER, 1, 0), ErrorCode.INVALID_STATE_TRANSITION);
        ReflectionTestUtils.setField(rental, "startDate", LocalDate.parse("2026-10-10"));
        when(rentals.findByItemIdAndStatusOrderById(10L, RentalStatus.ACTIVE)).thenReturn(List.of(mock(Rental.class)));
        error(() -> call(RentalCommand.Action.HANDOVER, 1, 0), ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    @DisplayName("비공개 물건도 기존 거래 정리 가능하며 외부 승인 정책 미적용")
    void cleanupDoesNotRequireNewRelationshipPolicy() {
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        rental.approve(NOW);
        call(RentalCommand.Action.HANDOVER, 1, 0);
        call(RentalCommand.Action.RETURN, 1, 1);
        verifyNoInteractions(policy);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    @DisplayName("반납 약속 대비 서버 확인 시각으로 준수 여부 저장")
    void snapshotsReturnPromise(long seconds) {
        prepare(RentalCommand.Action.RETURN);
        AppointmentProposal proposal = mock(AppointmentProposal.class);
        when(proposal.getScheduledAt()).thenReturn(NOW.plusSeconds(seconds));
        when(rentals.findConfirmedReturnProposal(100L)).thenReturn(Optional.of(proposal));
        call(RentalCommand.Action.RETURN, 1, 0);
        assertThat(rental.getReturnPromiseAt()).isEqualTo(NOW.plusSeconds(seconds));
        assertThat(rental.getReturnPromiseKept()).isEqualTo(seconds >= 0);
    }

    @Test
    @DisplayName("멱등 재전송은 현재 상태·버전과 무관하게 이전 응답 반환, 조회 권한 재검사")
    void replayChecksOnlyParticipantAccess() {
        RentalDetailStatusResponse replay = mock(RentalDetailStatusResponse.class);
        when(effects.findCommandReplay(anyLong(), anyString(), any(), any())).thenReturn(Optional.of(replay));
        ReflectionTestUtils.setField(rental, "status", RentalStatus.RETURNED);
        assertThat(call(RentalCommand.Action.APPROVE, 1, 99)).isSameAs(replay);
        error(() -> call(RentalCommand.Action.APPROVE, 3, 99), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(items, histories, queries);
    }

    @Test
    @DisplayName("멱등 저장소 조회 실패 시 상태 저장 금지")
    void missingPersistenceFailsClosed() {
        when(effects.findCommandReplay(anyLong(), anyString(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        error(() -> call(RentalCommand.Action.APPROVE, 1, 0), ErrorCode.DEPENDENCY_UNAVAILABLE);
        verifyNoInteractions(items, histories);
        verify(rentals, never()).findByIdForUpdate(anyLong());
    }

    private void prepare(RentalCommand.Action action) {
        if (action == RentalCommand.Action.HANDOVER || action == RentalCommand.Action.RETURN) rental.approve(NOW);
        if (action == RentalCommand.Action.RETURN) rental.handover(NOW);
    }

    private RentalDetailStatusResponse call(RentalCommand.Action action, long actor, long version) {
        var login = new LoginMember(actor, MemberRole.USER);
        return switch (action) {
            case APPROVE -> service.approveRental(100L, KEY, new RentalVersionRequest(version), login);
            case REJECT -> service.rejectRental(100L, KEY, new RejectRentalRequest(version, "일정 변경"), login);
            case CANCEL -> service.cancelRental(100L, KEY, new CancelRentalRequest(version, "일정 변경"), login);
            case HANDOVER -> service.handoverRental(100L, KEY, new RentalVersionRequest(version), login);
            case RETURN -> service.returnRental(100L, KEY, new RentalVersionRequest(version), login);
        };
    }

    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
