package com.billim.domain.notification.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * notifications — 회원별 앱 내 알림.
 * title은 생성 당시의 표시 문구 스냅샷이다. 나중에 물건 제목이 바뀌어도 알림 문구는 그대로 둔다. (ERD 4.6)
 */
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notifications_event_recipient", columnNames = {"event_id", "recipient_id"}),
        indexes = {
                @Index(name = "idx_notifications_recipient_read", columnList = "recipient_id,read_at,id"),
                @Index(name = "idx_notifications_recipient_id", columnList = "recipient_id,id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    public static final int TITLE_MAX = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, updatable = false)
    private Long recipientId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, updatable = false)
    private EventType type;

    @Column(nullable = false, length = TITLE_MAX, updatable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20, updatable = false)
    private NotificationTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "read_at")
    private Instant readAt;

    public static Notification of(long recipientId, long eventId, EventType type, String title,
                                  NotificationTargetType targetType, long targetId) {
        Notification notification = new Notification();
        notification.recipientId = recipientId;
        notification.eventId = eventId;
        notification.type = type;
        notification.title = truncate(title);
        notification.targetType = targetType;
        notification.targetId = targetId;
        return notification;
    }

    public boolean isReceivedBy(long memberId) {
        return recipientId == memberId;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 최초 읽은 시각을 유지한다. 이미 읽었으면 아무것도 바꾸지 않는다 (API 명세 D_050) */
    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }

    /** 문구가 길어도 알림 생성은 실패시키지 않는다. 컬럼 길이에 맞춰 자른다 */
    private static String truncate(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("알림 문구가 비어 있습니다.");
        }
        return title.length() <= TITLE_MAX ? title : title.substring(0, TITLE_MAX);
    }
}
