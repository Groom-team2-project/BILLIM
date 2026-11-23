package com.billim.domain.notification.dto;

import com.billim.domain.notification.entity.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 알림 목록 (API 명세 NotificationPage). id DESC, nextCursor는 다음 페이지의 exclusive 기준.
 * 더 받을 것이 없으면 nextCursor를 생략한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationPageResponse(
        List<NotificationResponse> items,
        boolean hasMore,
        String nextCursor
) {

    /**
     * @param found limit보다 한 건 더 조회한 결과. 초과분으로 다음 페이지 존재를 판단한다
     * @param limit 요청한 페이지 크기
     */
    public static NotificationPageResponse of(List<Notification> found, int limit) {
        boolean hasMore = found.size() > limit;
        List<Notification> page = hasMore ? found.subList(0, limit) : found;
        String nextCursor = hasMore ? String.valueOf(page.getLast().getId()) : null;
        return new NotificationPageResponse(
                page.stream().map(NotificationResponse::from).toList(), hasMore, nextCursor);
    }
}
