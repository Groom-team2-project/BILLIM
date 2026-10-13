package com.billim.domain.notification.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 알림 행의 읽음 규칙과 문구 스냅샷 (API 명세 D_050, ERD 4.6) */
class NotificationTest {

    private static final Instant FIRST = Instant.parse("2026-10-10T01:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-10T02:00:00Z");
    private static final long RECIPIENT = 7L;

    private Notification notification() {
        return Notification.of(RECIPIENT, 100L, EventType.RENTAL_APPROVED,
                "정우 님이 전동드릴 대여 요청을 승인했어요", NotificationTargetType.RENTAL, 501L);
    }

    @Test
    @DisplayName("새 알림은 미열람이다")
    void newNotificationIsUnread() {
        assertThat(notification().isRead()).isFalse();
        assertThat(notification().getReadAt()).isNull();
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽어도 최초 readAt을 유지한다")
    void secondReadKeepsFirstReadAt() {
        Notification notification = notification();

        notification.markRead(FIRST);
        notification.markRead(LATER);

        assertThat(notification.getReadAt()).isEqualTo(FIRST);
    }

    @Test
    @DisplayName("수신자 본인만 자기 알림으로 판정한다")
    void onlyRecipientOwnsNotification() {
        Notification notification = notification();

        assertThat(notification.isReceivedBy(RECIPIENT)).isTrue();
        assertThat(notification.isReceivedBy(RECIPIENT + 1)).isFalse();
    }

    @Test
    @DisplayName("문구가 컬럼 길이를 넘으면 잘라서 저장하고 알림 생성은 실패시키지 않는다")
    void longTitleIsTruncated() {
        String tooLong = "가".repeat(Notification.TITLE_MAX + 10);

        Notification notification = Notification.of(RECIPIENT, 100L, EventType.RENTAL_REQUESTED,
                tooLong, NotificationTargetType.RENTAL, 501L);

        assertThat(notification.getTitle()).hasSize(Notification.TITLE_MAX);
    }

    @Test
    @DisplayName("빈 문구로는 알림을 만들지 않는다")
    void blankTitleIsRejected() {
        assertThatThrownBy(() -> Notification.of(RECIPIENT, 100L, EventType.RENTAL_REQUESTED,
                "  ", NotificationTargetType.RENTAL, 501L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
