package com.billim.domain.member.dto;

import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.member.entity.Member;
import com.fasterxml.jackson.annotation.JsonInclude;

/** API 명세 PublicProfile. 소셜 식별자·개인 연락처는 미포함 (A_007) */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicProfileResponse(
        MemberSummaryResponse member,
        String communityName,
        boolean isVerified,
        boolean blockedByMe
) {

    /** @param membership 대상의 유효 활성 소속. 없으면 null */
    public static PublicProfileResponse of(Member member, MembershipResponse membership, boolean blockedByMe) {
        return new PublicProfileResponse(
                MemberSummaryResponse.from(member),
                membership == null ? null : membership.communityName(),
                membership != null && membership.isValid(),
                blockedByMe);
    }
}
