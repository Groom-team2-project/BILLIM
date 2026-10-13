package com.billim.domain.notification.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * event_deliveries — 소비자별 사건 중복 방지.
 * 소비 결과(알림·시스템 메시지)와 같은 트랜잭션에서 기록해 재소비에도 결과가 늘지 않게 한다. (ERD 4.6)
 */
@Entity
@Table(
        name = "event_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_event_deliveries_event_consumer", columnNames = {"event_id", "consumer_key"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventDelivery extends BaseTimeEntity {

    public static final int CONSUMER_KEY_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    /** notification:{memberId} 또는 room:{roomId} */
    @Column(name = "consumer_key", nullable = false, length = CONSUMER_KEY_MAX, updatable = false)
    private String consumerKey;

    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt;

    public static EventDelivery of(long eventId, String consumerKey, Instant now) {
        EventDelivery delivery = new EventDelivery();
        delivery.eventId = eventId;
        delivery.consumerKey = consumerKey;
        delivery.deliveredAt = now;
        return delivery;
    }
}
