package com.billim.domain.community.dto;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;

import java.time.Instant;

/**
 * 소속·인증 결과 (API 명세 Membership).
 * 인증에 쓴 좌표는 담지 않는다. 남는 것은 동네와 확인 시점뿐이다.
 */
public record MembershipResponse(
        String id,
        String communityId,
        String communityName,
        String status,
        Instant verifiedAt,
        Instant verificationExpiresAt,
        boolean isValid
) {

    public static MembershipResponse of(CommunityMembership membership, Community community, Instant now) {
        return new MembershipResponse(
                String.valueOf(membership.getId()),
                String.valueOf(membership.getCommunityId()),
                community.getName(),
                membership.getStatus().name(),
                membership.getVerifiedAt(),
                membership.getVerificationExpiresAt(),
                membership.isValidAt(now));
    }
}
