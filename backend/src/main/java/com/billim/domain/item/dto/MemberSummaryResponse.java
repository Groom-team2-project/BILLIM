package com.billim.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * @deprecated 물건 도메인은 더 이상 쓰지 않는다. 회원 도메인의 {@code com.billim.domain.member.dto.MemberSummaryResponse}를 쓴다.
 *             대여 도메인(RentalSummaryResponse)이 아직 이 타입을 import하고 있어 삭제하지 않고 남겨 둔다.
 *             TODO: 대여 쪽 import가 회원 도메인 DTO로 바뀌면 이 파일을 삭제한다.
 */
@Deprecated(forRemoval = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberSummaryResponse(String id, String displayName, Instant joinedAt) {

    public static MemberSummaryResponse ofId(long memberId) {
        return new MemberSummaryResponse(String.valueOf(memberId), null, null);
    }
}
