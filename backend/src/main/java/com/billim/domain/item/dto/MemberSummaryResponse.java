package com.billim.domain.item.dto;

import com.billim.domain.item.port.MemberPort.MemberSummary;

import java.time.Instant;

public record MemberSummaryResponse(String id, String displayName, Instant joinedAt) {

    public static MemberSummaryResponse from(MemberSummary m) {
        return new MemberSummaryResponse(String.valueOf(m.id()), m.displayName(), m.joinedAt());
    }
}
