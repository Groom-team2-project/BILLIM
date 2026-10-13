package com.billim.domain.notification.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** 사건 점유·완료·재시도 규칙 (ERD 4.6) */
class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-10-10T01:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(1);
    private static final Duration BACKOFF = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 3;

    private OutboxEvent event() {
        return OutboxEvent.pending("rental:501:version:2:approved",
                EventType.RENTAL_APPROVED, 501L, 2L, "{}", NOW);
    }

    @Test
    @DisplayName("적재한 사건은 즉시 소비 대기 상태다")
    void pendingEventIsImmediatelyClaimable() {
        OutboxEvent event = event();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAvailableAt()).isEqualTo(NOW);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getAggregateType()).isEqualTo(AggregateType.RENTAL);
    }

    @Test
    @DisplayName("점유하면 시도 횟수가 늘고 lease 기간이 설정된다")
    void leaseIncrementsAttempts() {
        OutboxEvent event = event();

        event.lease(NOW, LEASE);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLeaseUntil()).isEqualTo(NOW.plus(LEASE));
    }

    @Test
    @DisplayName("lease가 지난 PROCESSING 사건은 다시 점유 대상이다")
    void expiredLeaseIsReclaimable() {
        OutboxEvent event = event();
        event.lease(NOW, LEASE);

        assertThat(event.leaseExpired(NOW.plus(LEASE).minusSeconds(1))).isFalse();
        assertThat(event.leaseExpired(NOW.plus(LEASE).plusSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("실패하면 시도 횟수에 비례해 지연한 뒤 재시도한다")
    void failureSchedulesBackoff() {
        OutboxEvent event = event();
        event.lease(NOW, LEASE);

        event.fail(event.getAttempts(), NOW, BACKOFF, MAX_ATTEMPTS);

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getLeaseUntil()).isNull();
        assertThat(event.getAvailableAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    @DisplayName("재시도 한도를 소진하면 FAILED로 멈춘다")
    void failureStopsAfterMaxAttempts() {
        OutboxEvent event = event();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            event.lease(NOW, LEASE);
            event.fail(event.getAttempts(), NOW, BACKOFF, MAX_ATTEMPTS);
        }

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("완료하면 처리 시각이 남고 점유가 풀린다")
    void completeRecordsProcessedAt() {
        OutboxEvent event = event();
        event.lease(NOW, LEASE);

        event.complete(event.getAttempts(), NOW.plusSeconds(1));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(event.getProcessedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.getLeaseUntil()).isNull();
    }

    @Test
    @DisplayName("재선점 후 이전 시도의 완료와 실패는 현재 상태를 바꾸지 않는다")
    void staleAttemptCannotChangeReclaimedEvent() {
        OutboxEvent event = event();
        event.lease(NOW, LEASE);
        int oldAttempt = event.getAttempts();
        event.lease(NOW.plus(LEASE).plusSeconds(1), LEASE);

        assertThat(event.complete(oldAttempt, NOW.plusSeconds(2))).isFalse();
        assertThat(event.fail(oldAttempt, NOW.plusSeconds(2), BACKOFF, MAX_ATTEMPTS)).isFalse();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getAttempts()).isEqualTo(oldAttempt + 1);
        assertThat(event.complete(event.getAttempts(), NOW.plusSeconds(3))).isTrue();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DONE);
    }
}
