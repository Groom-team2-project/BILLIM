package com.billim.domain.rental.service;

import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 신규 요청·승인에 적용하는 동네·차단·제재·장소 정책 */
@Component
@RequiredArgsConstructor
public class RentalRequestPolicy {
    private final CommunityMembershipQuery memberships;
    private final BlockQuery blocks;
    private final SanctionService sanctions;
    private final CommunityPlaceRepository places;
    private final MemberRepository members;

    public void requireViewable(Item item, long memberId) {
        if (item.isDeleted()) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (item.isOwnedBy(memberId)) return;
        if (item.getVisibility() != ItemVisibility.PUBLIC
                || !memberships.hasValidMembership(memberId, item.getCommunityId())
                || blocks.isBlockedBetween(memberId, item.getOwnerId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    public String requireRequestAllowed(Item item, long memberId) {
        requireRelationship(item, memberId);
        return requirePlace(item.getPlaceId(), item.getCommunityId());
    }

    /** 공개 상태를 대여 가능 기준으로 사용 */
    public void requireAvailable(Item item) {
        if (item.isDeleted() || item.getVisibility() != ItemVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    public void requireApprovalAllowed(Item item, Rental rental) {
        requireRelationship(item, rental.getBorrowerId());
        requirePlace(rental.getPlaceId(), rental.getCommunityId());
    }

    /** 조회 응답의 승인 버튼 조건. 실제 승인에서는 잠금 아래 재검사 */
    public boolean canApprove(Item item, Rental rental) {
        return !item.isDeleted() && item.getVisibility() == ItemVisibility.PUBLIC
                && active(item.getOwnerId()) && active(rental.getBorrowerId())
                && memberships.hasValidMembership(item.getOwnerId(), item.getCommunityId())
                && memberships.hasValidMembership(rental.getBorrowerId(), item.getCommunityId())
                && !blocks.isBlockedBetween(item.getOwnerId(), rental.getBorrowerId())
                && !sanctions.isRestricted(item.getOwnerId()) && !sanctions.isRestricted(rental.getBorrowerId())
                && places.findByIdAndCommunityId(rental.getPlaceId(), rental.getCommunityId())
                        .filter(com.billim.domain.community.entity.CommunityPlace::isActive).isPresent();
    }

    private void requireRelationship(Item item, long borrowerId) {
        if (!active(item.getOwnerId()) || !active(borrowerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!memberships.hasValidMembership(item.getOwnerId(), item.getCommunityId())
                || !memberships.hasValidMembership(borrowerId, item.getCommunityId())) {
            throw new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        }
        if (blocks.isBlockedBetween(item.getOwnerId(), borrowerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (sanctions.isRestricted(item.getOwnerId()) || sanctions.isRestricted(borrowerId)) {
            throw new BusinessException(ErrorCode.MEMBER_RESTRICTED);
        }
    }

    private boolean active(long memberId) {
        return members.findById(memberId).filter(Member::isActive).isPresent();
    }

    private String requirePlace(long placeId, long communityId) {
        return places.findByIdAndCommunityId(placeId, communityId)
                .filter(com.billim.domain.community.entity.CommunityPlace::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST)).getName();
    }
}
