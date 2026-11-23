package com.billim.domain.safety.dto;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.member.entity.Member;
import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.service.ReportTarget;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 관리자용 신고 상세 (API 명세 AdminReport).
 * 신고자 정보와 internalNote는 이 응답에만 담는다. 신고자 정보를 제재 대상에게 노출하지 않기 위해
 * 일반 신고 응답({@link ReportResponse})과 형식을 분리한다. (API 명세 13.2, ERD 4.6)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminReportResponse(
        ReportResponse report,
        MemberSummaryResponse reporter,
        String communityId,
        String evidenceText,
        String internalNote
) {

    public static AdminReportResponse of(Report report, Member reporter, Member targetMember,
                                         ReportAction action, ReportTarget.ReportSnapshot snapshot) {
        return new AdminReportResponse(
                ReportResponse.of(report, targetMember, action),
                MemberSummaryResponse.from(reporter),
                String.valueOf(report.getCommunityId()),
                evidenceOf(snapshot),
                action == null ? null : action.getInternalNote());
    }

    /** 증거 본문이 없으면(회원 신고 등) 대상 표시 이름을 대신 보여준다 */
    private static String evidenceOf(ReportTarget.ReportSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.evidenceText() != null ? snapshot.evidenceText() : snapshot.targetLabel();
    }
}
