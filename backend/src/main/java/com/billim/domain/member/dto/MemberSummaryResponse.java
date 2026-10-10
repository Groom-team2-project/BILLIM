package com.billim.domain.member.dto;

import com.billim.domain.member.entity.Member;

import java.time.Instant;

/**
 * API 명세 MemberSummary
 * id는 BIGINT를 JSON 문자열로 전달 (명세 0.1)
 */
public record MemberSummaryResponse(String id, String displayName, Instant joinedAt) {

    public static MemberSummaryResponse from(Member member) {
        return new MemberSummaryResponse(
                String.valueOf(member.getId()), member.getDisplayName(), member.getCreatedAt());
    }
}
