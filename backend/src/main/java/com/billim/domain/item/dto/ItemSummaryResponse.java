package com.billim.domain.item.dto;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItemSummaryResponse(
        String id,
        String title,
        CategoryResponse category,
        MemberSummaryResponse owner,
        PlaceResponse place,
        String thumbnailUrl,
        String visibility,
        Integer distanceMeters,   // TODO(E): 동네 중심·장소 좌표 연동 전에는 null(생략)
        String distanceBasis,
        Boolean availableForRange,   // TODO(C): 대여 점유 연동 전에는 null(생략)
        Instant createdAt,
        Integer pendingRequestCount,   // TODO(C): 대기 요청 수 연동 전에는 null(생략)
        long version
) {
}
