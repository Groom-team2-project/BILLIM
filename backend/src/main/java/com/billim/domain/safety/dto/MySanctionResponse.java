package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.entity.Sanction;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 내가 받은 운영 조치 한 건 (API 명세 MySanction).
 *
 * <h2>담는 것과 담지 않는 것</h2>
 * 조치 종류와 운영자가 쓴 <b>최소 사유</b>(publicReason), 제재 기간만 담는다.
 * 신고자 정보, 운영 메모(internalNote), 신고 사유 코드, 신고 대상 거래·메시지는 담지 않는다.
 *
 * <p>대상 거래를 담지 않는 이유: 신고 대상이 물건·메시지일 때 그 거래를 특정해 보여주면
 * 그 물건을 빌린 사람이 한 명인 경우 <b>신고자가 사실상 특정</b>된다. 보복 신고를 막기 위한
 * 신고자 비공개 원칙과 충돌한다. 대상에게 알릴 맥락은 운영자가 publicReason에 적는다.
 * (API 명세 12.6, ERD 4.6)
 *
 * <p>경고(WARN)는 제재 행이 없어 kind·startsAt·endsAt이 비고 isEffective는 false다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MySanctionResponse(
        String id,
        String action,
        String publicReason,
        Instant decidedAt,
        String kind,
        Instant startsAt,
        Instant endsAt,
        Instant revokedAt,
        boolean isEffective
) {

    /**
     * @param sanction 조치가 만든 제재. 경고는 null
     */
    public static MySanctionResponse of(ReportAction action, Sanction sanction, Instant now) {
        return new MySanctionResponse(
                String.valueOf(action.getId()),
                action.getAction().name(),
                action.getPublicReason(),
                action.getCreatedAt(),
                sanction == null ? null : sanction.getKind().name(),
                sanction == null ? null : sanction.getStartsAt(),
                sanction == null ? null : sanction.getEndsAt(),
                sanction == null ? null : sanction.getRevokedAt(),
                sanction != null && sanction.isEffectiveAt(now));
    }
}
