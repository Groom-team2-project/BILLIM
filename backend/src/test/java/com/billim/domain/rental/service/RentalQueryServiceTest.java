package com.billim.domain.rental.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.dto.RentalAvailabilityResponse.DateRange;
import com.billim.domain.rental.dto.RentalRole;
import com.billim.domain.rental.entity.*;
import com.billim.domain.rental.repository.*;
import com.billim.global.exception.*;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RentalQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-10");
    private static final LoginMember BORROWER = new LoginMember(2L, MemberRole.USER);
    private RentalRepository rentals;
    private RentalStatusHistoryRepository histories;
    private MemberRepository members;
    private ItemRepository items;
    private RentalRequestPolicy policy;
    private RentalQueryService service;

    private final AppointmentService appointments = mock(AppointmentService.class);

    @BeforeEach
    void setUp() {
        when(appointments.getAppointments(anyLong(), any())).thenReturn(new com.billim.domain.rental.dto.AppointmentSlotsResponse(java.util.List.of()));
        rentals = mock(RentalRepository.class);
        histories = mock(RentalStatusHistoryRepository.class);
        members = mock(MemberRepository.class);
        items = mock(ItemRepository.class);
        policy = mock(RentalRequestPolicy.class);
        service = new RentalQueryService(rentals, histories, members, items, policy, Clock.fixed(NOW, ZoneOffset.UTC), appointments);
        for (long id = 1; id <= 3; id++) {
            Member member = Member.register("회원" + id);
            ReflectionTestUtils.setField(member, "id", id);
            when(members.findById(id)).thenReturn(Optional.of(member));
        }
        Item item = Item.create(1L, 1L, 1L, 1L, "수정한 제목", null, TODAY, TODAY.plusDays(60), NOW);
        when(items.findById(10L)).thenReturn(Optional.of(item));
    }

    @Test
    @DisplayName("채팅 내부 요약은 참여자만 조회하고 무관한 회원은 404")
    void internalSummaryChecksParticipant() {
        Rental rental = rental(TODAY, TODAY.plusDays(2));
        when(rentals.findById(100L)).thenReturn(Optional.of(rental));
        assertThat(service.getRentalSummary(100L, 2L).id()).isEqualTo("100");
        error(() -> service.getRentalSummary(100L, 3L), ErrorCode.RESOURCE_NOT_FOUND);
        error(() -> service.getRentalSummary(100L, null), ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("거래 당사자는 현재 물건 공개 권한과 무관하게 스냅샷·상태 이력 조회")
    void detailUsesSnapshotsAndPreservesNullActor() {
        Rental rental = rental(TODAY, TODAY.plusDays(2));
        when(rentals.findById(100L)).thenReturn(Optional.of(rental));
        RentalStatusHistory history = RentalStatusHistory.requested(rental);
        ReflectionTestUtils.setField(history, "actorId", null);
        when(histories.findByRentalIdOrderByRentalVersionAsc(100L)).thenReturn(List.of(history));
        var response = service.getRentalDetailStatus(100L, BORROWER);
        assertThat(response.rental().itemTitle()).isEqualTo("거래 당시 제목");
        assertThat(response.rental().allowedActions()).containsExactly("CANCEL");
        assertThat(response.history()).singleElement().satisfies(h -> assertThat(h.actorId()).isNull());
        assertThat(response.appointments()).isEmpty();
        verifyNoInteractions(policy, items);
        error(() -> service.getRentalDetailStatus(100L, new LoginMember(3L, MemberRole.USER)), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("목록 응답의 연체·반납일까지 남은 일수·페이지 정보")
    void listComputesOverdueAtBoundary() {
        Rental rental = rental(TODAY.minusDays(2), TODAY.minusDays(1));
        ReflectionTestUtils.setField(rental, "status", RentalStatus.ACTIVE);
        when(rentals.findAll(org.mockito.ArgumentMatchers.<Specification<Rental>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rental), PageRequest.of(0, 1), 2));
        var result = service.getRentalList(RentalRole.BORROWER, null, true, null, 0, 1, BORROWER);
        assertThat(result.items()).singleElement().satisfies(r -> {
            assertThat(r.overdue()).isTrue();
            assertThat(r.dDay()).isEqualTo(-1);
            assertThat(r.allowedActions()).isEmpty();
        });
        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalElements()).isEqualTo(2);
        error(() -> service.getRentalList(RentalRole.BORROWER, RentalStatus.RETURNED, true, null, 0, 20, BORROWER),
                ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("달력 점유 구간 잘라내기·겹침 및 연속 구간 병합·거래 ID 미노출")
    void clipsAndMergesOccupiedRanges() {
        when(rentals.findOccupied(anyLong(), any(), any(), any())).thenReturn(List.of(
                rental(TODAY.minusDays(2), TODAY.plusDays(1)),
                rental(TODAY.plusDays(1), TODAY.plusDays(3)),
                rental(TODAY.plusDays(4), TODAY.plusDays(5)),
                rental(TODAY.plusDays(8), TODAY.plusDays(12))));
        var result = service.getAvailability(10L, TODAY, TODAY.plusDays(10), null, null, BORROWER);
        assertThat(result.occupiedRanges()).containsExactly(
                new DateRange(TODAY, TODAY.plusDays(5)), new DateRange(TODAY.plusDays(8), TODAY.plusDays(10)));
        assertThat(result.selectedRangeAvailable()).isNull();
        assertThat(result.checkedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("달력 표시 범위 밖의 선택 기간도 별도 점유 확인")
    void selectedRangeDoesNotUseOnlyVisibleCalendar() {
        when(rentals.hasOccupiedDates(eq(10L), any(), eq(TODAY.plusDays(20)), eq(TODAY.plusDays(21)))).thenReturn(true);
        var result = service.getAvailability(10L, TODAY, TODAY.plusDays(5), TODAY.plusDays(20), TODAY.plusDays(21), BORROWER);
        assertThat(result.occupiedRanges()).isEmpty();
        assertThat(result.selectedRangeAvailable()).isFalse();
    }

    @Test
    @DisplayName("93일 달력·30일 선택 허용, 초과·과거·날짜 한쪽 누락 거부")
    void availabilityValidatesInclusiveLimits() {
        assertThat(service.getAvailability(10L, TODAY, TODAY.plusDays(92), TODAY, TODAY.plusDays(29), BORROWER)
                .selectedRangeAvailable()).isTrue();
        error(() -> service.getAvailability(10L, TODAY, TODAY.plusDays(93), null, null, BORROWER), ErrorCode.INVALID_REQUEST);
        error(() -> service.getAvailability(10L, TODAY, TODAY.plusDays(92), TODAY, TODAY.plusDays(30), BORROWER), ErrorCode.INVALID_REQUEST);
        error(() -> service.getAvailability(10L, TODAY, TODAY.plusDays(1), TODAY, null, BORROWER), ErrorCode.INVALID_REQUEST);
        error(() -> service.getAvailability(10L, TODAY, TODAY.plusDays(1), TODAY.minusDays(1), TODAY, BORROWER), ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("연체 중에는 빈 달력 구간이어도 선택 불가")
    void overdueBlocksSelection() {
        when(rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(10L, RentalStatus.ACTIVE, NOW)).thenReturn(true);
        var result = service.getAvailability(10L, TODAY, TODAY.plusDays(5), TODAY, TODAY, BORROWER);
        assertThat(result.overdueBlocked()).isTrue();
        assertThat(result.selectedRangeAvailable()).isFalse();
    }

    @Test
    @DisplayName("달력 접근 정책 거부 시 점유 정보 조회 금지")
    void calendarRequiresViewPermission() {
        doThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE)).when(policy).requireViewable(any(), anyLong());
        error(() -> service.getAvailability(10L, TODAY, TODAY, null, null, BORROWER), ErrorCode.DEPENDENCY_UNAVAILABLE);
        verifyNoInteractions(rentals);
    }

    @Test
    @DisplayName("빈 거래의 상태별 건수와 예정 목록은 0과 빈 배열")
    void summaryOfNoRentals() {
        var result = service.getRentalCountStatus(BORROWER);
        assertThat(result.borrowed().active()).isZero();
        assertThat(result.borrowed().overdue()).isZero();
        assertThat(result.lent().requested()).isZero();
        assertThat(result.nextRentals()).isEmpty();
    }

    private Rental rental(LocalDate start, LocalDate end) {
        Rental rental = Rental.request(10L, 1L, 2L, 1L, start, end,
                start.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
                end.plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), "거래 당시 제목", 1L, "정문");
        ReflectionTestUtils.setField(rental, "id", 100L);
        return rental;
    }

    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
