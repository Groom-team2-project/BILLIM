package com.billim.domain.item.service;

import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

// 물건 조회 권한 판정. 동네 소속은 커뮤니티 도메인(CommunityMembershipQuery), 차단은 안전 도메인(BlockQuery) 판정 사용
@Component
public class ItemAccessPolicy {

    private final CommunityMembershipQuery membershipQuery;
    private final BlockQuery blockQuery;

    public ItemAccessPolicy(CommunityMembershipQuery membershipQuery, BlockQuery blockQuery) {
        this.membershipQuery = membershipQuery;
        this.blockQuery = blockQuery;
    }

    // 유효한 활성 동네 ID. 소속이 없거나 인증이 만료되면 빈 값
    public Optional<Long> findActiveCommunityId(long memberId) {
        return membershipQuery.findActiveCommunityId(memberId);
    }

    public long requireActiveCommunityId(long memberId) {
        return findActiveCommunityId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));
    }

    // 해당 동네의 유효 소속이 아니면 403 (수정·공개 복원)
    public void requireValidMembership(long memberId, long communityId) {
        if (!membershipQuery.hasValidMembership(memberId, communityId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        }
    }

    // 후보 소유자 중 나와 차단 관계(양방향)인 회원
    public Set<Long> findBlockedOwners(long memberId, Collection<Long> ownerIds) {
        return blockQuery.findBlockedAmong(memberId, ownerIds);
    }

    /** 본인은 HIDDEN도 조회. 타인은 같은 유효 동네의 PUBLIC이면서 차단 관계가 아닌 경우만. DELETED는 모두 불가 */
    public boolean canView(Item item, long memberId) {
        if (item.isDeleted()) {
            return false;
        }
        if (item.isOwnedBy(memberId)) {
            return true;
        }
        if (item.getVisibility() != ItemVisibility.PUBLIC) {
            return false;
        }
        Optional<Long> active = findActiveCommunityId(memberId);
        if (active.isEmpty() || !active.get().equals(item.getCommunityId())) {
            return false;
        }
        return !blockQuery.isBlockedBetween(memberId, item.getOwnerId());
    }
}
