package com.billim.domain.member.dto;

import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 명세 MyProfile
 * activeMembership 생략은 유효 소속 없음이며 onboardingRequired와 같은 조건
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyProfileResponse(
        MemberSummaryResponse member,
        String role,
        String status,
        boolean isRestricted,
        MembershipResponse activeMembership,
        boolean onboardingRequired,
        long version
) {

    public static MyProfileResponse from(Member member, boolean isRestricted, MembershipResponse activeMembership) {
        return new MyProfileResponse(
                MemberSummaryResponse.from(member),
                member.getRole().name(),
                member.getStatus().name(),
                isRestricted,
                activeMembership,
                activeMembership == null,
                member.getVersion());
    }
}
