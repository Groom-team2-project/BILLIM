package com.billim.domain.safety.dto;

import java.util.List;

/** 제재 이력 (API 명세 SanctionPage) */
public record SanctionPageResponse(
        List<SanctionResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
