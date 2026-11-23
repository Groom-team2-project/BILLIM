package com.billim.domain.rental.service;

import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.global.exception.BusinessException;
import org.junit.jupiter.api.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalTrustServiceTest {
    private final RentalRepository rentals = mock(RentalRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final CommunityMembershipQuery memberships = mock(CommunityMembershipQuery.class);
    private final BlockQuery blocks = mock(BlockQuery.class);
    private final RentalTrustService service = new RentalTrustService(rentals, members, memberships, blocks);

    @BeforeEach
    void setUp() {
        when(members.findById(1L)).thenReturn(Optional.of(Member.register("본인")));
        when(members.findById(2L)).thenReturn(Optional.of(Member.register("이웃")));
    }

    @Test @DisplayName("본인은 동네·차단과 무관하게 거래 0건 지표 조회")
    void selfWithNoRentals() {
        var result = service.get(1L, 1L);
        assertThat(result.completedCount()).isZero();
        assertThat(result.returnPromiseTotal()).isZero();
        verifyNoInteractions(memberships, blocks);
    }

    @Test @DisplayName("완료는 빌린·빌려준 거래 합계, 준수는 확정 반납 약속이 있던 빌린 거래만 집계")
    void countsCompletedRoles() {
        when(rentals.countByBorrowerIdAndStatus(1L, RentalStatus.RETURNED)).thenReturn(3L);
        when(rentals.countByOwnerIdAndStatus(1L, RentalStatus.RETURNED)).thenReturn(2L);
        when(rentals.countByBorrowerIdAndStatusAndReturnPromiseKeptTrue(1L, RentalStatus.RETURNED)).thenReturn(1L);
        when(rentals.countByBorrowerIdAndStatusAndReturnPromiseKeptIsNotNull(1L, RentalStatus.RETURNED)).thenReturn(2L);
        var result = service.get(1L, 1L);
        assertThat(result.completedCount()).isEqualTo(5);
        assertThat(result.lentCount()).isEqualTo(2);
        assertThat(result.returnPromiseKept()).isEqualTo(1);
        assertThat(result.returnPromiseTotal()).isEqualTo(2);
    }

    @Test @DisplayName("기존 거래 상대는 동네·차단 변경 후에도 조회")
    void existingTradePreservesAccess() {
        when(rentals.hasTraded(1L, 2L)).thenReturn(true);
        assertThat(service.get(2L, 1L).completedCount()).isZero();
        verifyNoInteractions(memberships, blocks);
    }

    @Test @DisplayName("기존 거래 없으면 같은 유효 동네이고 차단이 없어야 조회")
    void requiresSameCommunityAndNoBlock() {
        assertThatThrownBy(() -> service.get(2L, 1L)).isInstanceOf(BusinessException.class);
        when(memberships.findActiveCommunityId(1L)).thenReturn(Optional.of(10L));
        when(memberships.hasValidMembership(2L, 10L)).thenReturn(true);
        assertThat(service.get(2L, 1L)).isNotNull();
        when(blocks.isBlockedBetween(1L, 2L)).thenReturn(true);
        assertThatThrownBy(() -> service.get(2L, 1L)).isInstanceOf(BusinessException.class);
    }

    @Test @DisplayName("비로그인과 없는 대상 회원 조회 거부")
    void rejectsAnonymousAndMissingMember() {
        assertThatThrownBy(() -> service.get(2L, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.get(3L, 1L)).isInstanceOf(BusinessException.class);
    }
}
