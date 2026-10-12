package com.billim.domain.safety.dto;

import java.util.List;

/** 내 신고 목록 (API 명세 ReportPage) */
public record ReportPageResponse(
        List<ReportResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
