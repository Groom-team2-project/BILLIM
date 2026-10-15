package com.billim.domain.community.dto;

import com.billim.domain.community.entity.Community;

import java.math.BigDecimal;

/**
 * 동네 정보 (API 명세 Community).
 * verifiedMemberCount는 유효 인증 회원 수이며 조회 시점 기준 파생값이다.
 */
public record CommunityResponse(
        String id,
        String name,
        String displayAddress,
        BigDecimal centerLatitude,
        BigDecimal centerLongitude,
        int verificationRadiusM,
        int verifiedMemberCount
) {

    public static CommunityResponse of(Community community, long verifiedMemberCount) {
        return new CommunityResponse(
                String.valueOf(community.getId()),
                community.getName(),
                community.getDisplayAddress(),
                community.getCenterLatitude(),
                community.getCenterLongitude(),
                community.getVerificationRadiusM(),
                Math.toIntExact(verifiedMemberCount));
    }
}
