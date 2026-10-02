package com.billim.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 물건 상세 (API 명세 ItemDetail) */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemDetailResponse(
        String id,
        String title,
        String description,
        MemberSummaryResponse owner,
        String communityId,
        CategoryResponse category,
        PlaceResponse place,
        List<ImageResponse> images,
        LocalDate availableStartDate,
        LocalDate availableEndDate,
        String visibility,
        int distanceMeters,
        String distanceBasis,
        long version,
        List<String> allowedActions,
        Instant createdAt
) {
}
