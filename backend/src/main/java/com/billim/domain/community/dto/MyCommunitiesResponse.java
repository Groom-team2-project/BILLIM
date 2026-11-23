package com.billim.domain.community.dto;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;

import java.time.Instant;
import java.util.List;

/**
 * 내 동네 목록 (API 명세 E_074).
 *
 * <p><b>저장 동네</b>는 둘까지고 <b>현재 동네</b>는 그중 하나다. 거래·채팅 권한은 저장 동네
 * 전체를 보고, 홈 피드·물건 목록은 현재 동네를 기준으로 한다.
 * (도메인 가이드 결정 '저장 동네와 현재 동네')
 *
 * @param items             저장 동네. 슬롯 순서라 추가한 순서와 대체로 같다
 * @param currentCommunityId 현재 동네. 저장 동네가 없으면 null
 */
public record MyCommunitiesResponse(List<MyCommunity> items, String currentCommunityId) {

    /**
     * 저장 동네 한 건.
     *
     * @param isCurrent 지금 피드 기준이 되는 동네인지
     * @param isValid   인증이 만료되지 않았는지. 만료되면 재인증해야 거래할 수 있다
     */
    public record MyCommunity(
            String communityId,
            String communityName,
            String displayAddress,
            boolean isCurrent,
            boolean isValid,
            Instant verifiedAt,
            Instant verificationExpiresAt
    ) {

        public static MyCommunity of(CommunityMembership membership, Community community,
                                     Long currentCommunityId, Instant now) {
            return new MyCommunity(
                    String.valueOf(membership.getCommunityId()),
                    community.getName(),
                    community.getDisplayAddress(),
                    membership.getCommunityId().equals(currentCommunityId),
                    membership.isValidAt(now),
                    membership.getVerifiedAt(),
                    membership.getVerificationExpiresAt());
        }
    }
}
