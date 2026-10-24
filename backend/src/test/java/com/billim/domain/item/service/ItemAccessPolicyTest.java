package com.billim.domain.item.service;

import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 상세·사진 조회 권한 (API 명세 B_020). 동네 소속은 CommunityMembershipQuery, 차단은 BlockQuery 판정
@ExtendWith(MockitoExtension.class)
class ItemAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final long OWNER = 1L;
    private static final long VIEWER = 2L;
    private static final long COMMUNITY = 100L;

    @Mock CommunityMembershipQuery membershipQuery;
    @Mock BlockQuery blockQuery;

    ItemAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ItemAccessPolicy(membershipQuery, blockQuery);
    }

    private Item item() {
        return Item.create(OWNER, COMMUNITY, 1L, 10L, "전동드릴", null,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10), NOW);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("활성 동네는 커뮤니티 도메인의 유효 소속 판정을 따른다")
    void activeCommunityComesFromMembershipQuery() {
        when(membershipQuery.findActiveCommunityId(VIEWER)).thenReturn(Optional.of(COMMUNITY));
        assertThat(policy.findActiveCommunityId(VIEWER)).contains(COMMUNITY);
        assertThat(policy.requireActiveCommunityId(VIEWER)).isEqualTo(COMMUNITY);
    }

    @Test
    @DisplayName("유효 소속이 없으면(인증 만료 포함) 403 COMMUNITY_VERIFICATION_REQUIRED다")
    void noValidMembershipIsForbidden() {
        when(membershipQuery.findActiveCommunityId(VIEWER)).thenReturn(Optional.empty());
        assertCode(() -> policy.requireActiveCommunityId(VIEWER), ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
    }

    @Test
    @DisplayName("특정 동네의 유효 소속이 아니면 403이고, 유효하면 통과한다")
    void requireValidMembershipChecksTheGivenCommunity() {
        when(membershipQuery.hasValidMembership(OWNER, COMMUNITY)).thenReturn(false);
        assertCode(() -> policy.requireValidMembership(OWNER, COMMUNITY), ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);

        when(membershipQuery.hasValidMembership(OWNER, COMMUNITY)).thenReturn(true);
        policy.requireValidMembership(OWNER, COMMUNITY);
    }

    @Test
    @DisplayName("본인은 숨김 물건도 볼 수 있고 동네·차단 조회를 하지 않는다")
    void ownerSeesHiddenItemWithoutLookups() {
        Item item = item();
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        assertThat(policy.canView(item, OWNER)).isTrue();
        verify(membershipQuery, never()).findActiveCommunityId(anyLong());
        verify(blockQuery, never()).isBlockedBetween(anyLong(), anyLong());
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
    }

    @Test
    @DisplayName("같은 유효 동네의 공개 물건은 차단 관계가 없으면 볼 수 있다")
    void sameCommunityPublicItemIsVisible() {
        when(membershipQuery.findActiveCommunityId(VIEWER)).thenReturn(Optional.of(COMMUNITY));
        when(blockQuery.isBlockedBetween(VIEWER, OWNER)).thenReturn(false);
        assertThat(policy.canView(item(), VIEWER)).isTrue();
    }

    @Test
    @DisplayName("차단 관계(어느 방향이든)인 소유자의 물건은 볼 수 없다")
    void blockedOwnersItemIsInvisible() {
        when(membershipQuery.findActiveCommunityId(VIEWER)).thenReturn(Optional.of(COMMUNITY));
        when(blockQuery.isBlockedBetween(VIEWER, OWNER)).thenReturn(true);
        assertThat(policy.canView(item(), VIEWER)).isFalse();
    }

    @Test
    @DisplayName("다른 동네 회원이나 유효 소속이 없는 회원은 볼 수 없다")
    void otherCommunityOrNoMembershipCannotSee() {
        when(membershipQuery.findActiveCommunityId(VIEWER)).thenReturn(Optional.of(999L));
        assertThat(policy.canView(item(), VIEWER)).isFalse();

        when(membershipQuery.findActiveCommunityId(VIEWER)).thenReturn(Optional.empty());
        assertThat(policy.canView(item(), VIEWER)).isFalse();
        verify(blockQuery, never()).isBlockedBetween(anyLong(), anyLong());
    }

    @Test
    @DisplayName("검색 제외 대상은 후보를 모으지 않고 차단 관계 회원만 한 번에 조회한다")
    void findBlockRelatedMembersDelegatesInOneCall() {
        when(blockQuery.findBlockRelatedMemberIds(VIEWER)).thenReturn(Set.of(3L));
        assertThat(policy.findBlockRelatedMembers(VIEWER)).containsExactly(3L);
        verify(blockQuery, never()).findBlockedAmong(anyLong(), any());
    }
}
