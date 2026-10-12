package com.billim.domain.notification.dto;

import com.billim.domain.notification.entity.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 알림 한 건 (API 명세 Notification). BIGINT ID는 JSON 문자열로 내린다 (명세 0.1).
 * readAt은 미열람이면 생략한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        String id,
        String type,
        String title,
        String targetType,
        String targetId,
        Instant readAt,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                String.valueOf(notification.getId()),
                notification.getType().name(),
                notification.getTitle(),
                notification.getTargetType().name(),
                String.valueOf(notification.getTargetId()),
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
