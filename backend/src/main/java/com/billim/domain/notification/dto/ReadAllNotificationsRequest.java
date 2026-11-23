package com.billim.domain.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 기준 ID까지 모두 읽음 (API 명세 ReadAllNotifications).
 * throughId는 목록에서 관측한 본인 알림 ID다. 이보다 큰 신규 알림은 미열람으로 남는다.
 */
public record ReadAllNotificationsRequest(
        @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ID 형식이 올바르지 않습니다.")
        String throughId
) {

    public long throughIdAsLong() {
        return Long.parseLong(throughId);
    }
}
