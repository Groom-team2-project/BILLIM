package com.billim.domain.rental.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.entity.MemberStatus;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.dto.CreateRentalRequest;
import com.billim.domain.rental.dto.RentalDetailStatusResponse;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.rental.repository.RentalStatusHistoryRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RentalServiceTest {
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";
    // UTC 전날이지만 KST는 10월 10일인 경계
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final LoginMember LOGIN = new LoginMember(2L, MemberRole.USER);
    private RentalRepository rentals;
    private RentalStatusHistoryRepository histories;
    private ItemRepository items;
    private MemberRepository members;
    private RentalRequestPolicy policy;
    private RentalRequestEffects effects;
    private ChatRoomRepository chatRooms;
    private ChatParticipantRepository chatParticipants;
    private RentalService service;
    private Item item;

    @BeforeEach
    void setUp() {
        rentals = mock(RentalRepository.class);
        histories = mock(RentalStatusHistoryRepository.class);
        items = mock(ItemRepository.class);
        members = mock(MemberRepository.class);
        policy = mock(RentalRequestPolicy.class);
        effects = mock(RentalRequestEffects.class);
        chatRooms = mock(ChatRoomRepository.class);
        chatParticipants = mock(ChatParticipantRepository.class);
        service = new RentalService(rentals, histories, items, members, policy, effects,
                Clock.fixed(NOW, ZoneOffset.UTC), mock(RentalQueryService.class), mock(com.billim.domain.rental.repository.AppointmentSlotRepository.class),
                chatRooms, chatParticipants);
        Member borrower = member(2L, "요청자");
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(borrower));
        when(members.findById(1L)).thenReturn(Optional.of(member(1L, "소유자")));
        when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member(1L, "소유자")));
        when(items.findOwnerId(10L)).thenReturn(Optional.of(1L));
        when(effects.findReplay(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());
        item = Item.create(1L, 1L, 1L, 1L, "드릴", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31), NOW);
        ReflectionTestUtils.setField(item, "id", 10L);
        when(items.findByIdForUpdate(10L)).thenReturn(Optional.of(item));
        when(policy.requireRequestAllowed(item, 2L)).thenReturn("정문");
        when(chatRooms.saveAndFlush(any())).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            ReflectionTestUtils.setField(room, "id", 200L);
            return room;
        });
        when(rentals.saveAndFlush(any())).thenAnswer(invocation -> {
            Rental rental = invocation.getArgument(0);
            ReflectionTestUtils.setField(rental, "id", 100L);
            ReflectionTestUtils.setField(rental, "createdAt", NOW);
            return rental;
        });
        when(histories.saveAndFlush(any())).thenAnswer(invocation -> {
            Object history = invocation.getArgument(0);
            ReflectionTestUtils.setField(history, "createdAt", NOW);
            return history;
        });
    }

    @Test
    @DisplayName("KST 오늘 요청 생성·스냅샷·만료 시각·이력·이벤트 기록")
    void createsTodayRequest() {
        var response = create("2026-10-10", "2026-10-11");
        assertThat(response.rental().status()).isEqualTo(RentalStatus.REQUESTED);
        assertThat(response.rental().itemTitle()).isEqualTo("드릴");
        assertThat(response.rental().placeName()).isEqualTo("정문");
        assertThat(response.rental().dDay()).isZero();
        assertThat(response.rental().version()).isZero();
        assertThat(response.requestExpiresAt()).isEqualTo(Instant.parse("2026-10-10T15:00:00Z"));
        assertThat(response.dueAt()).isEqualTo(Instant.parse("2026-10-11T15:00:00Z"));
        assertThat(response.history()).singleElement().satisfies(h -> {
            assertThat(h.fromStatus()).isNull();
            assertThat(h.toStatus()).isEqualTo(RentalStatus.REQUESTED);
            assertThat(h.actorId()).isEqualTo("2");
        });
        assertThat(response.appointments()).isEmpty();
        assertThat(response.chatRoomId()).isEqualTo("200");
        verify(policy).requireAvailable(item);
        verify(chatRooms).saveAndFlush(any());
        verify(chatParticipants).saveAll(argThat(participants -> {
            int count = 0;
            for (var participant : participants) {
                if (participant.getRoomId() != 200L ||
                    (participant.getMemberId() != 1L && participant.getMemberId() != 2L)) return false;
                count++;
            }
            return count == 2;
        }));
        verify(effects).recordSuccess(eq(2L), eq(KEY), any(), any(), eq(response), eq(NOW));
        var order = inOrder(members, items, rentals, histories, effects);
        order.verify(members).findByIdForUpdate(2L);
        order.verify(effects).findReplay(eq(2L), eq(KEY), any(), eq(NOW));
        order.verify(items).findByIdForUpdate(10L);
        order.verify(rentals).saveAndFlush(any());
        order.verify(histories).saveAndFlush(any());
        order.verify(effects).recordSuccess(anyLong(), anyString(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"2026-10-10,2026-10-10", "2026-10-10,2026-11-08"})
    @DisplayName("당일 및 양끝 포함 30일 요청 허용")
    void acceptsInclusiveLimit(String start, String end) {
        assertThat(create(start, end).rental().status()).isEqualTo(RentalStatus.REQUESTED);
    }

    @ParameterizedTest
    @CsvSource({"2026-10-09,2026-10-10", "2026-10-11,2026-10-10", "2026-10-10,2026-11-09",
            "2026-12-30,2027-01-01"})
    @DisplayName("과거·역전·31일·물건 가능 기간 초과 거부")
    void rejectsInvalidDates(String start, String end) {
        assertError(() -> create(start, end), ErrorCode.INVALID_REQUEST);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("미인증 요청은 DB 접근 전 거부")
    void rejectsAnonymous() {
        assertError(() -> service.createRental(KEY, request(), null), ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(members, rentals, items);
    }

    @Test
    @DisplayName("탈퇴 회원 요청 거부")
    void rejectsWithdrawnMember() {
        Member withdrawn = member(2L, "탈퇴자");
        ReflectionTestUtils.setField(withdrawn, "status", MemberStatus.WITHDRAWN);
        when(members.findByIdForUpdate(2L)).thenReturn(Optional.of(withdrawn));
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(effects);
    }

    @Test
    @DisplayName("본인 물건 요청 거부")
    void rejectsOwnItem() {
        ReflectionTestUtils.setField(item, "ownerId", 2L);
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.FORBIDDEN);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("비공개 물건 요청 거부")
    void rejectsHiddenItem() {
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("물건 대여 상태 미연동 시 저장 없이 거부")
    void failsClosedWithoutAvailability() {
        doThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE)).when(policy).requireAvailable(item);
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.DEPENDENCY_UNAVAILABLE);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("동네·차단·제재 검증 실패 시 거래 저장 금지")
    void rejectsPolicyFailure() {
        when(policy.requireRequestAllowed(item, 2L)).thenThrow(new BusinessException(ErrorCode.MEMBER_RESTRICTED));
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.MEMBER_RESTRICTED);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("연체 물건에 신규 요청 거부")
    void rejectsOverdueItem() {
        when(rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(10L, RentalStatus.ACTIVE, NOW)).thenReturn(true);
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.ITEM_OVERDUE);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("승인·대여 중·반납 완료 점유와 충돌 검사")
    void rejectsOccupiedDates() {
        when(rentals.hasOccupiedDates(eq(10L), any(), any(), any())).thenReturn(true);
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.RENTAL_DATE_CONFLICT);
        verify(rentals).hasOccupiedDates(eq(10L), eq(java.util.List.of(
                RentalStatus.APPROVED, RentalStatus.ACTIVE, RentalStatus.RETURNED)), any(), any());
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("같은 회원의 겹치는 대기 요청 거부")
    void rejectsDuplicatePending() {
        when(rentals.hasPendingRequest(eq(10L), eq(2L), any(), any())).thenReturn(true);
        assertError(() -> create("2026-10-10", "2026-10-11"), ErrorCode.DUPLICATE_RENTAL_REQUEST);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("재전송은 거래 조회 권한 확인 후 최초 응답 반환·재생성 없음")
    void replaysWithoutCommandValidation() {
        RentalDetailStatusResponse response = create("2026-10-10", "2026-10-11");
        Rental original = mock(Rental.class);
        when(original.getBorrowerId()).thenReturn(2L);
        when(rentals.findById(100L)).thenReturn(Optional.of(original));
        when(effects.findReplay(anyLong(), anyString(), any(), any())).thenReturn(Optional.of(response));
        clearInvocations(rentals, histories, items, policy, effects);
        assertThat(service.createRental(KEY, request(), LOGIN)).isSameAs(response);
        verify(rentals, never()).saveAndFlush(any());
        verifyNoInteractions(histories, policy);
        verify(items, never()).findByIdForUpdate(anyLong());
        verify(effects, never()).recordSuccess(anyLong(), anyString(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("같은 키 다른 본문 오류 전파·재생성 없음")
    void rejectsReusedKey() {
        when(effects.findReplay(anyLong(), anyString(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertError(() -> service.createRental(KEY, request(), LOGIN), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        verify(rentals, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("멱등 저장소 조회 실패 시 성공 응답 금지")
    void failsClosedWithoutPersistence() {
        when(effects.findReplay(anyLong(), anyString(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE));
        assertError(() -> service.createRental(KEY, request(), LOGIN), ErrorCode.DEPENDENCY_UNAVAILABLE);
        verifyNoInteractions(rentals, histories);
        verify(items, never()).findByIdForUpdate(anyLong());
    }

    private RentalDetailStatusResponse create(String start, String end) {
        return service.createRental(KEY, new CreateRentalRequest("10", LocalDate.parse(start), LocalDate.parse(end)), LOGIN);
    }

    private CreateRentalRequest request() {
        return new CreateRentalRequest("10", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11));
    }

    private Member member(long id, String name) {
        Member member = Member.register(name);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "createdAt", NOW.minusSeconds(86400));
        return member;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
