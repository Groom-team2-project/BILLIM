package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.Notice;

import java.time.Instant;

/** 공지 한 건 (API 명세 Notice). 작성자 정보는 담지 않는다 */
public record NoticeResponse(
        String id,
        String communityId,
        String title,
        String body,
        boolean pinned,
        long version,
        Instant createdAt
) {

    public static NoticeResponse from(Notice notice) {
        return new NoticeResponse(
                String.valueOf(notice.getId()),
                String.valueOf(notice.getCommunityId()),
                notice.getTitle(),
                notice.getBody(),
                notice.isPinned(),
                notice.getVersion(),
                notice.getCreatedAt());
    }
}
