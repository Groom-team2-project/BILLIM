package com.billim.domain.item.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 상세·사진 조회 권한 (API 명세 B_020).
 * 활성 동네는 회원이 선택한 동네(members.active_community_id)를 읽고, 조회 권한 경로는 spy로 소속 조회만 대체해 확인한다.
 */
class ItemAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final long OWNER = 1L;
    private static final long VIEWER = 2L;
    private static final long COMMUNITY = 100L;

    MemberRepository memberRepository;
    ItemAccessPolicy policy;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        policy = spy(new ItemAccessPolicy(memberRepository));
    }

    /** 활성 동네를 가진 회원 (status는 가입 시 ACTIVE) */
    private static Member member(Long activeCommunityId) {
        Member m = Member.register("이웃회원");
        ReflectionTestUtils.setField(m, "activeCommunityId", activeCommunityId);
        return m;
    }

    private Item item() {
        return Item.create(OWNER, COMMUNITY, 1L, 10L, "전동드릴", null,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10), NOW);
    }

    /** 활성 동네 조회(TODO(E))를 지정한 값으로 대체 */
    private void viewerLivesIn(Long communityId) {
        doReturn(Optional.ofNullable(communityId)).when(policy).findActiveCommunityId(VIEWER);
    }

    @Test
    @DisplayName("활성 동네는 회원이 선택한 동네(members.active_community_id)다")
    void activeCommunityComesFromMembersTable() {
        when(memberRepository.findById(VIEWER)).thenReturn(Optional.of(member(COMMUNITY)));
        assertThat(new ItemAccessPolicy(memberRepository).findActiveCommunityId(VIEWER)).contains(COMMUNITY);
    }

    @Test
    @DisplayName("선택한 동네가 없거나 회원이 없으면 활성 동네가 없다")
    void noSelectedCommunityOrMissingMemberMeansNoCommunity() {
        when(memberRepository.findById(VIEWER)).thenReturn(Optional.of(member(null)));
        assertThat(new ItemAccessPolicy(memberRepository).findActiveCommunityId(VIEWER)).isEmpty();

        when(memberRepository.findById(VIEWER)).thenReturn(Optional.empty());
        assertThat(new ItemAccessPolicy(memberRepository).findActiveCommunityId(VIEWER)).isEmpty();
    }

    @Test
    @DisplayName("탈퇴 등 활동 중이 아닌 회원은 활성 동네가 없다")
    void inactiveMemberHasNoCommunity() {
        Member withdrawn = member(COMMUNITY);
        ReflectionTestUtils.setField(withdrawn, "status", com.billim.domain.member.entity.MemberStatus.WITHDRAWN);
        when(memberRepository.findById(VIEWER)).thenReturn(Optional.of(withdrawn));
        assertThat(new ItemAccessPolicy(memberRepository).findActiveCommunityId(VIEWER)).isEmpty();
    }

    @Test
    @DisplayName("유효한 소속이 없으면 403 COMMUNITY_VERIFICATION_REQUIRED다")
    void requireActiveCommunityRejectsMemberWithoutMembership() {
        viewerLivesIn(null);
        assertThatThrownBy(() -> policy.requireActiveCommunityId(VIEWER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));
    }

    @Test
    @DisplayName("본인은 숨김 물건도 볼 수 있고 동네 조회를 하지 않는다")
    void ownerSeesHiddenItemWithoutCommunityLookup() {
        Item item = item();
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        assertThat(policy.canView(item, OWNER)).isTrue();
        verify(policy, never()).findActiveCommunityId(anyLong());
    }

    @Test
    @DisplayName("삭제된 물건은 본인도 볼 수 없다")
    void deletedItemIsInvisibleEvenToOwner() {
        Item item = item();
        item.delete(NOW);
        assertThat(policy.canView(item, OWNER)).isFalse();
        assertThat(policy.canView(item, VIEWER)).isFalse();
    }

    @Test
    @DisplayName("타인은 숨김 물건을 볼 수 없다")
    void othersCannotSeeHiddenItem() {
        Item item = item();
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        assertThat(policy.canView(item, VIEWER)).isFalse();
        verify(policy, never()).findActiveCommunityId(anyLong());
    }

    @Test
    @DisplayName("같은 유효 동네의 공개 물건은 볼 수 있다 (차단 제외는 TODO(E))")
    void sameCommunityPublicItemIsVisible() {
        viewerLivesIn(COMMUNITY);
        assertThat(policy.canView(item(), VIEWER)).isTrue();
    }

    @Test
    @DisplayName("다른 동네 회원이나 유효 소속이 없는 회원은 볼 수 없다")
    void otherCommunityOrNoMembershipCannotSee() {
        viewerLivesIn(999L);
        assertThat(policy.canView(item(), VIEWER)).isFalse();

        viewerLivesIn(null);
        assertThat(policy.canView(item(), VIEWER)).isFalse();
    }

    @Test
    @DisplayName("같은 동네를 선택한 회원은 타인의 공개 물건을 볼 수 있다 (회원 테이블 기준)")
    void viewerInSameCommunityByMembersTableCanSee() {
        when(memberRepository.findById(VIEWER)).thenReturn(Optional.of(member(COMMUNITY)));
        assertThat(new ItemAccessPolicy(memberRepository).canView(item(), VIEWER)).isTrue();
    }
}
