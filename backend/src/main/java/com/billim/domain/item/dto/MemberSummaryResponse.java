package com.billim.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 회원 요약 (API 명세 MemberSummary).
 * TODO(A): displayName·joinedAt은 회원 도메인 연동 후 채운다. 연동 전에는 id만 내려간다(null 필드는 응답에서 생략).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberSummaryResponse(String id, String displayName, Instant joinedAt) {

    public static MemberSummaryResponse ofId(long memberId) {
        return new MemberSummaryResponse(String.valueOf(memberId), null, null);
    }
}
