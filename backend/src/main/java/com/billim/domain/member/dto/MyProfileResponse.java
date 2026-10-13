package com.billim.domain.member.dto;

import com.billim.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 명세 MyProfile
 * activeMembership은 community_memberships 연동 전이라 항상 생략. 선택 필드라 계약 위반 아님.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyProfileResponse(
        MemberSummaryResponse member,
        String role,
        String status,
        boolean isRestricted,
        boolean onboardingRequired,
        long version
) {

    public static MyProfileResponse from(Member member) {
        return new MyProfileResponse(
                MemberSummaryResponse.from(member),
                member.getRole().name(),
                member.getStatus().name(),
                // TODO: sanctions 연동 시 제재 판정으로 교체
                false,
                member.getActiveCommunityId() == null,
                member.getVersion());
    }
}
