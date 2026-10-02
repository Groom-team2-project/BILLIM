package com.billim.domain.item.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.port.BlockPort;
import com.billim.domain.item.port.CommunityPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 상세·사진 조회 권한 (API 명세 B_020): 본인은 HIDDEN 포함, 타인은 같은 유효 동네 PUBLIC·차단 없음만 */
@ExtendWith(MockitoExtension.class)
class ItemAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final long OWNER = 1L;
    private static final long VIEWER = 2L;
    private static final long COMMUNITY = 100L;

    @Mock CommunityPort communityPort;
    @Mock BlockPort blockPort;
    @InjectMocks ItemAccessPolicy policy;

    private Item item() {
        return Item.create(OWNER, COMMUNITY, 1L, 10L, "전동드릴", null,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 10), NOW);
    }

    @Test
    @DisplayName("본인은 숨김 물건도 볼 수 있다")
    void ownerSeesHiddenItem() {
        Item item = item();
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        assertThat(policy.canView(item, OWNER)).isTrue();
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
        verify(communityPort, never()).findActiveCommunityId(anyLong());
    }

    @Test
    @DisplayName("같은 유효 동네의 공개 물건은 차단 관계가 없으면 볼 수 있다")
    void sameCommunityPublicItemIsVisible() {
        when(communityPort.findActiveCommunityId(VIEWER)).thenReturn(Optional.of(COMMUNITY));
        when(blockPort.findBlockRelatedMemberIds(VIEWER)).thenReturn(Set.of());
        assertThat(policy.canView(item(), VIEWER)).isTrue();
    }

    @Test
    @DisplayName("다른 동네 회원이나 유효 소속이 없는 회원은 볼 수 없다")
    void otherCommunityOrNoMembershipCannotSee() {
        when(communityPort.findActiveCommunityId(VIEWER)).thenReturn(Optional.of(999L));
        assertThat(policy.canView(item(), VIEWER)).isFalse();

        when(communityPort.findActiveCommunityId(VIEWER)).thenReturn(Optional.empty());
        assertThat(policy.canView(item(), VIEWER)).isFalse();
    }

    @Test
    @DisplayName("차단 관계인 소유자의 물건은 볼 수 없다")
    void blockedOwnersItemIsInvisible() {
        when(communityPort.findActiveCommunityId(VIEWER)).thenReturn(Optional.of(COMMUNITY));
        when(blockPort.findBlockRelatedMemberIds(VIEWER)).thenReturn(Set.of(OWNER));
        assertThat(policy.canView(item(), VIEWER)).isFalse();
    }
}
