package com.billim.domain.item.dto;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        Integer distanceMeters,   // 동네 중심 기준 거리(m). 좌표가 없으면 생략
        String distanceBasis,
        long version,
        List<String> allowedActions,
        Instant createdAt
) {
}
