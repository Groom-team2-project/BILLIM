package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * 커밋된 사건을 소비자에게 전달한다. 상태 변경 트랜잭션 밖에서 동작하므로
 * 알림 생성이 늦어지거나 실패해도 거래 자체는 영향을 받지 않는다. (ERD 4.6, 정책 및 상태 5)
 *
 * <p>확장: {@link OutboxConsumer} 빈을 추가하면 이 디스패처가 자동으로 함께 호출한다.
 * 디스패처 코드는 소비자 종류를 알지 않는다.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventStore store;
    private final List<OutboxConsumer> consumers;
    private final Clock clock;
    private final int batchSize;
    private final Duration lease;
    private final Duration retryBackoff;
    private final int maxAttempts;

    public OutboxDispatcher(
            OutboxEventStore store,
            List<OutboxConsumer> consumers,
            Clock clock,
            @Value("${billim.notification.outbox.batch-size:50}") int batchSize,
            @Value("${billim.notification.outbox.lease:1m}") Duration lease,
            @Value("${billim.notification.outbox.retry-backoff:10s}") Duration retryBackoff,
            @Value("${billim.notification.outbox.max-attempts:5}") int maxAttempts) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("billim.notification.outbox.batch-size must be at least 1");
        }
        this.store = store;
        this.consumers = List.copyOf(consumers);
        this.clock = clock;
        this.batchSize = batchSize;
        this.lease = lease;
        this.retryBackoff = retryBackoff;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 한 번에 batchSize만큼 처리한다.
     *
     * @return 이번 호출에서 완료 처리한 사건 수
     */
    public int dispatchBatch() {
        List<OutboxEventStore.ClaimedEvent> claimed = store.claim(clock.instant(), batchSize, lease);
        int completed = 0;
        for (OutboxEventStore.ClaimedEvent claim : claimed) {
            if (dispatchOne(claim)) {
                completed++;
            }
        }
        return completed;
    }

    private boolean dispatchOne(OutboxEventStore.ClaimedEvent claim) {
        OutboxEvent event = store.find(claim.eventId()).orElse(null);
        if (event == null) {
            log.warn("점유한 사건이 사라졌음: eventId={}", claim.eventId());
            return false;
        }

        try {
            for (OutboxConsumer consumer : consumers) {
                deliverAll(event, consumer);
            }
            return store.complete(claim, clock.instant());
        } catch (RuntimeException e) {
            // 처리에 성공한 소비 단위는 이미 커밋돼 있다. 재시도는 남은 단위만 다시 본다
            store.fail(claim, clock.instant(), retryBackoff, maxAttempts);
            log.error("사건 소비 실패: eventId={} eventKey={}", claim.eventId(), event.getEventKey(), e);
            return false;
        }
    }

    private void deliverAll(OutboxEvent event, OutboxConsumer consumer) {
        for (String consumerKey : consumer.consumerKeys(event)) {
            try {
                store.deliver(event.getId(), consumer, consumerKey, clock.instant());
            } catch (OutboxEventStore.AlreadyDeliveredException e) {
                // 다른 작업자가 먼저 처리한 단위. 이 사건의 나머지 단위는 계속 처리한다
                log.debug("중복 처리 건너뜀: eventId={} consumerKey={}", event.getId(), consumerKey);
            }
        }
    }
}
