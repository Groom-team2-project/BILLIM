package com.billim.domain.safety.dto;

import java.util.List;

/** 내 차단 목록 (API 명세 BlockPage) */
public record BlockPageResponse(
        List<BlockResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
