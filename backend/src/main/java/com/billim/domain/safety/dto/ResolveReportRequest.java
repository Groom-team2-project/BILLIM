package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.entity.ReportActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 신고 처리·제재 결정 (API 명세 ResolveReport) */
public record ResolveReportRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull ReportActionType action,
        @NotBlank @Size(max = ReportAction.PUBLIC_REASON_MAX) String publicReason,
        @Size(max = ReportAction.INTERNAL_NOTE_MAX) String internalNote
) {
}
