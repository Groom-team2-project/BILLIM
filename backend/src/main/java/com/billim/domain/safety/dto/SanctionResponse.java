package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.Sanction;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** 제재 한 건 (API 명세 Sanction). isEffective는 조회 시점 기준 파생값이다 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SanctionResponse(
        String id,
        String memberId,
        String kind,
        Instant startsAt,
        Instant endsAt,
        Instant revokedAt,
        boolean isEffective
) {

    public static SanctionResponse of(Sanction sanction, Instant now) {
        return new SanctionResponse(
                String.valueOf(sanction.getId()),
                String.valueOf(sanction.getMemberId()),
                sanction.getKind().name(),
                sanction.getStartsAt(),
                sanction.getEndsAt(),
                sanction.getRevokedAt(),
                sanction.isEffectiveAt(now));
    }
}
