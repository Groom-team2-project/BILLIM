package com.billim.domain.safety.dto;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.entity.Member;
import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportAction;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 신고 한 건 (API 명세 Report).
 * 처리 전에는 publicReason·action이 없다. internalNote와 신고자 정보는 담지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportResponse(
        String id,
        String targetType,
        String targetId,
        MemberSummaryResponse targetMember,
        String reasonCode,
        String detail,
        String status,
        String publicReason,
        String action,
        long version,
        Instant createdAt
) {

    /**
     * @param targetMember 대상 회원. 신고자에게도 대상이 누구인지는 보여준다
     * @param action       처리 이력. 미처리면 null
     */
    public static ReportResponse of(Report report, Member targetMember, ReportAction action) {
        return new ReportResponse(
                String.valueOf(report.getId()),
                report.getTargetType().name(),
                String.valueOf(report.targetId()),
                MemberSummaryResponse.from(targetMember),
                report.getReasonCode().name(),
                report.getDetail(),
                report.getStatus().name(),
                action == null ? null : action.getPublicReason(),
                action == null ? null : action.getAction().name(),
                report.getVersion(),
                report.getCreatedAt());
    }
}
