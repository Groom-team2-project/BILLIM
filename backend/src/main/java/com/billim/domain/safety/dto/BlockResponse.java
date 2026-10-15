package com.billim.domain.safety.dto;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.entity.Member;
import com.billim.domain.safety.entity.Block;

import java.time.Instant;

/** 차단한 이웃 한 명 (API 명세 Block) */
public record BlockResponse(MemberSummaryResponse member, Instant blockedAt) {

    public static BlockResponse of(Block block, Member blocked) {
        return new BlockResponse(MemberSummaryResponse.from(blocked), block.getCreatedAt());
    }
}
