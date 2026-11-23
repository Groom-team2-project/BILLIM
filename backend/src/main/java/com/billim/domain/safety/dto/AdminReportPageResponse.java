package com.billim.domain.safety.dto;

import java.util.List;

/** 운영 신고 목록 (API 명세 AdminReportPage) */
public record AdminReportPageResponse(
        List<AdminReportResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
