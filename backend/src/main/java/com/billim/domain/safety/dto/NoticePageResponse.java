package com.billim.domain.safety.dto;

import java.util.List;

/** 공지 목록 (API 명세 NoticePage) */
public record NoticePageResponse(
        List<NoticeResponse> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
}
