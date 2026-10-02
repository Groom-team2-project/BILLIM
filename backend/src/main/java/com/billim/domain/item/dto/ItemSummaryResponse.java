package com.billim.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** 물건 목록 항목 (API 명세 ItemSummary) */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemSummaryResponse(
        String id,
        String title,
        CategoryResponse category,
        MemberSummaryResponse owner,
        PlaceResponse place,
        String thumbnailUrl,
        String visibility,
        int distanceMeters,
        String distanceBasis,
        Boolean availableForRange,
        Instant createdAt,
        Integer pendingRequestCount,
        long version
) {
}
