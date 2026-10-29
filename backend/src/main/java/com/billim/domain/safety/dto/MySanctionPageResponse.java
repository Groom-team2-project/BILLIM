package com.billim.domain.safety.dto;

import java.util.List;

/** 내가 받은 운영 조치 내역 (API 명세 MySanctionPage) */
public record MySanctionPageResponse(
        List<MySanctionResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
