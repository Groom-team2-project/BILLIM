package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.entity.ReportTargetType;
import com.billim.domain.safety.entity.Sanction;
import com.billim.domain.safety.service.ReportTarget;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 내가 받은 운영 조치 한 건 (API 명세 MySanction).
 *
 * <h2>담는 것</h2>
 * 조치 종류, 운영자가 쓴 <b>최소 사유</b>(publicReason), 제재 기간, 그리고 <b>어떤 대상으로
 * 조치됐는지</b>(targetType·targetLabel·evidenceText)다. 대상 정보에 담기는 것은 모두
 * <b>대상 본인의 것</b>이다 — 물건은 본인이 등록한 물건의 제목·설명이고, 메시지는 본인이 보낸
 * 메시지 본문이다. 접수 시점 스냅샷이라 물건이 지워진 뒤에도 남는다.
 *
 * <h2>담지 않는 것</h2>
 * 신고자 정보, 운영 메모(internalNote), 신고 사유 코드.
 *
 * <h2>알려진 노출</h2>
 * 대상 정보를 보여주면 대상이 <b>어떤 거래로 조치됐는지</b> 알게 되고, 그 거래의 상대가 한 명이면
 * <b>신고자를 추정할 수 있다</b>. 메시지 신고는 대화 상대가 곧 신고자라 추정이 아니라 특정이다.
 * 대상이 무엇 때문에 조치됐는지 알아야 한다는 쪽을 택한 팀 결정이다.
 * (도메인 가이드 결정 '조치 사유를 누구에게 보여줄지')
 *
 * <p>경고(WARN)는 제재 행이 없어 kind·startsAt·endsAt이 비고 isEffective는 false다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MySanctionResponse(
        String id,
        String action,
        String publicReason,
        Instant decidedAt,
        String targetType,
        String targetId,
        String targetLabel,
        String evidenceText,
        String kind,
        Instant startsAt,
        Instant endsAt,
        Instant revokedAt,
        boolean isEffective
) {

    /**
     * @param snapshot 접수 시점 스냅샷. 깨져서 읽지 못하면 null — 조치 정보는 그대로 내려간다
     * @param sanction 조치가 만든 제재. 경고는 null
     */
    public static MySanctionResponse of(ReportAction action, Report report,
                                        ReportTarget.ReportSnapshot snapshot,
                                        Sanction sanction, Instant now) {
        return new MySanctionResponse(
                String.valueOf(action.getId()),
                action.getAction().name(),
                action.getPublicReason(),
                action.getCreatedAt(),
                report.getTargetType().name(),
                // 회원 신고의 대상 ID는 본인이라 알려 줄 것이 없다
                report.getTargetType() == ReportTargetType.MEMBER ? null : String.valueOf(report.targetId()),
                snapshot == null ? null : snapshot.targetLabel(),
                snapshot == null ? null : snapshot.evidenceText(),
                sanction == null ? null : sanction.getKind().name(),
                sanction == null ? null : sanction.getStartsAt(),
                sanction == null ? null : sanction.getEndsAt(),
                sanction == null ? null : sanction.getRevokedAt(),
                sanction != null && sanction.isEffectiveAt(now));
    }
}
