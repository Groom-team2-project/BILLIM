package com.billim.domain.item.service;

import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 상세·사진 조회 권한 (API 명세 B_020). 동네 소속 판정은 동네 도메인(E) 연동 전이라 비어 있다.
 * 연동 전 동작(503)을 그대로 검증하고, 소속이 있다고 가정한 경로는 spy로 소속 조회만 대체해 확인한다.
 */
class ItemAccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");
    private static final long OWNER = 1L;
    private static final long VIEWER = 2L;
    private static final long COMMUNITY = 100L;

    ItemAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = spy(new ItemAccessPolicy());
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
    @DisplayName("동네 연동 전에는 활성 동네를 지어내지 않고 503 DEPENDENCY_UNAVAILABLE이다")
    void activeCommunityIsUnavailableUntilCommunityDomainIsLinked() {
        assertThatThrownBy(() -> new ItemAccessPolicy().findActiveCommunityId(VIEWER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
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
    @DisplayName("동네 연동 전에는 타인의 공개 물건 조회가 503이다")
    void viewingOthersPublicItemIsUnavailableUntilCommunityIsLinked() {
        assertThatThrownBy(() -> new ItemAccessPolicy().canView(item(), VIEWER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE));
    }
}
