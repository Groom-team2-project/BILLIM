package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportReasonCode;
import com.billim.domain.safety.entity.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 신고 접수 (API 명세 CreateReport).
 * targetMember는 서버가 targetId에서 계산하므로 요청에 담지 않는다.
 * OTHER 사유의 detail 필수 규칙은 도메인 규칙이라 {@link Report#open}에서 검증한다.
 */
public record CreateReportRequest(
        @NotNull ReportTargetType targetType,
        @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ID 형식이 올바르지 않습니다.") String targetId,
        @NotNull ReportReasonCode reasonCode,
        @Size(max = Report.DETAIL_MAX) String detail
) {

    public long targetIdAsLong() {
        return Long.parseLong(targetId);
    }
}
