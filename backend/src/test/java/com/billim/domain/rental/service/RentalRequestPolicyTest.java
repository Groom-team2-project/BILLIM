package com.billim.domain.rental.service;

import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RentalRequestPolicyTest {
    private final CommunityMembershipQuery memberships = mock(CommunityMembershipQuery.class);
    private final BlockQuery blocks = mock(BlockQuery.class);
    private final SanctionService sanctions = mock(SanctionService.class);
    private final CommunityPlaceRepository places = mock(CommunityPlaceRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final RentalRequestPolicy policy = new RentalRequestPolicy(memberships, blocks, sanctions, places, members);
    private Item item;
    private Rental rental;
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    @BeforeEach
    void setUp() {
        LocalDate date = LocalDate.of(2026,10,15);
        item = Item.create(1L,10L,1L,20L,"드릴",null,date,date.plusDays(10),NOW);
        rental = Rental.request(100L,1L,2L,10L,date,date.plusDays(1),NOW.plusSeconds(3600),
                NOW.plusSeconds(86400),"드릴",20L,"정문");
        when(members.findById(1L)).thenReturn(Optional.of(Member.register("소유자")));
        when(members.findById(2L)).thenReturn(Optional.of(Member.register("대여자")));
        when(memberships.hasValidMembership(1L,10L)).thenReturn(true);
        when(memberships.hasValidMembership(2L,10L)).thenReturn(true);
        when(places.findByIdAndCommunityId(20L,10L)).thenReturn(Optional.of(place(true)));
    }

    @Test @DisplayName("유효 소속·차단 없음·제재 없음·활성 장소·공개 물건이면 요청과 승인 허용")
    void allowsValidRequestAndApproval() {
        policy.requireViewable(item,2L);
        policy.requireAvailable(item);
        assertThat(policy.requireRequestAllowed(item,2L)).isEqualTo("정문");
        policy.requireApprovalAllowed(item,rental);
        assertThat(policy.canApprove(item,rental)).isTrue();
    }

    @Test @DisplayName("소속 만료·차단·제재는 요청과 승인 모두 거부하고 승인 버튼 제외")
    void rejectsChangedRelationship() {
        when(memberships.hasValidMembership(1L,10L)).thenReturn(false);
        rejectsBoth(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        when(memberships.hasValidMembership(1L,10L)).thenReturn(true);
        when(blocks.isBlockedBetween(1L,2L)).thenReturn(true);
        rejectsBoth(ErrorCode.FORBIDDEN);
        when(blocks.isBlockedBetween(1L,2L)).thenReturn(false);
        when(sanctions.isRestricted(2L)).thenReturn(true);
        rejectsBoth(ErrorCode.MEMBER_RESTRICTED);
    }

    @Test @DisplayName("차단된 상대와 동네 인증 만료자는 예약 정보 조회 불가")
    void protectsAvailability() {
        when(blocks.isBlockedBetween(2L,1L)).thenReturn(true);
        assertError(() -> policy.requireViewable(item,2L),ErrorCode.RESOURCE_NOT_FOUND);
        when(blocks.isBlockedBetween(2L,1L)).thenReturn(false);
        when(memberships.hasValidMembership(2L,10L)).thenReturn(false);
        assertError(() -> policy.requireViewable(item,2L),ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test @DisplayName("비공개 물건은 소유자만 예약 조회 가능하며 신규 대여는 불가")
    void publicIsAvailabilityCriterion() {
        item.changeVisibility(ItemVisibility.HIDDEN,NOW);
        policy.requireViewable(item,1L);
        assertError(() -> policy.requireViewable(item,2L),ErrorCode.RESOURCE_NOT_FOUND);
        assertError(() -> policy.requireAvailable(item),ErrorCode.INVALID_STATE_TRANSITION);
        assertThat(policy.canApprove(item,rental)).isFalse();
    }

    @Test @DisplayName("비활성·다른 동네 장소는 요청과 승인 모두 불가")
    void requiresActivePlace() {
        when(places.findByIdAndCommunityId(20L,10L)).thenReturn(Optional.of(place(false)));
        rejectsBoth(ErrorCode.INVALID_REQUEST);
        when(places.findByIdAndCommunityId(20L,10L)).thenReturn(Optional.empty());
        rejectsBoth(ErrorCode.INVALID_REQUEST);
    }

    private CommunityPlace place(boolean active) {
        return CommunityPlace.of(10L,"정문",BigDecimal.valueOf(37),BigDecimal.valueOf(127),null,active);
    }

    private void rejectsBoth(ErrorCode code) {
        assertError(() -> policy.requireRequestAllowed(item,2L),code);
        assertError(() -> policy.requireApprovalAllowed(item,rental),code);
        assertThat(policy.canApprove(item,rental)).isFalse();
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
