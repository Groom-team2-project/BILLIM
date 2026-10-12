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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;

/**
 * outbox_events — 커밋된 도메인 사건.
 * 상태 변경과 같은 트랜잭션에 저장하고, 알림·시스템 메시지 생성은 커밋 후 소비자가 수행한다.
 * payload에는 인증 비밀값과 개인 좌표를 담지 않는다. (ERD 4.6)
 */
@Entity
@Table(
        name = "outbox_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_events_event_key", columnNames = "event_key"),
        indexes = @Index(name = "idx_outbox_events_status_available", columnList = "status,available_at,id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseTimeEntity {

    public static final int EVENT_KEY_MAX = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사건의 자연 키. 같은 상태 변경을 두 번 발행해도 한 건만 남는다 */
    @Column(name = "event_key", nullable = false, length = EVENT_KEY_MAX, updatable = false)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 20, updatable = false)
    private AggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private Long aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private Long aggregateVersion;

    /** EventPayload의 JSON 직렬화 결과. 해석은 소비자의 몫 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "processed_at")
    private Instant processedAt;

    private OutboxEvent(String eventKey, EventType eventType, long aggregateId,
                        long aggregateVersion, String payload, Instant now) {
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.aggregateType = eventType.aggregateType();
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.availableAt = now;
    }

    /** 상태 변경 트랜잭션에서 사건을 적재한다. 즉시 소비 대상(available_at = now) */
    public static OutboxEvent pending(String eventKey, EventType eventType, long aggregateId,
                                      long aggregateVersion, String payload, Instant now) {
        return new OutboxEvent(eventKey, eventType, aggregateId, aggregateVersion, payload, now);
    }

    /** 작업자가 점유한다. lease_until까지는 다른 작업자가 가져가지 않는다 */
    public void lease(Instant now, Duration leaseDuration) {
        this.status = OutboxStatus.PROCESSING;
        this.attempts = this.attempts + 1;
        this.leaseUntil = now.plus(leaseDuration);
    }

    public void complete(Instant now) {
        this.status = OutboxStatus.DONE;
        this.leaseUntil = null;
        this.processedAt = now;
    }

    /** 재시도 한도 안이면 지연 후 재시도, 넘었으면 FAILED로 멈춘다 */
    public void fail(Instant now, Duration retryBackoff, int maxAttempts) {
        if (attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
            this.leaseUntil = null;
            return;
        }
        this.status = OutboxStatus.PENDING;
        this.leaseUntil = null;
        this.availableAt = now.plus(retryBackoff.multipliedBy(attempts));
    }

    /** 점유가 만료됐는지. 작업자가 죽어 PROCESSING으로 남은 사건을 되살리는 기준 */
    public boolean leaseExpired(Instant now) {
        return status == OutboxStatus.PROCESSING && (leaseUntil == null || leaseUntil.isBefore(now));
    }
}
