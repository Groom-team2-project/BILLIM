package com.billim.domain.community.dto;

import java.util.List;

/** 동네 검색 목록 (API 명세 CommunityPage) */
public record CommunityPageResponse(
        List<CommunityResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
