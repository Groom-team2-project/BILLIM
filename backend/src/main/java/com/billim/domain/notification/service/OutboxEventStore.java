package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventDelivery;
import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.repository.EventDeliveryRepository;
import com.billim.domain.notification.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 사건 소비의 트랜잭션 경계. {@link OutboxDispatcher}가 단계마다 이 서비스를 호출한다.
 * 소비 단위 하나를 독립 트랜잭션으로 끊어, 한 수신자의 실패가 다른 수신자의 결과를 되돌리지 않게 한다.
 */
@Service
public class OutboxEventStore {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventStore.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventDeliveryRepository eventDeliveryRepository;

    public OutboxEventStore(OutboxEventRepository outboxEventRepository,
                            EventDeliveryRepository eventDeliveryRepository) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventDeliveryRepository = eventDeliveryRepository;
    }

    /**
     * 처리할 사건을 점유한다. 점유한 사건은 lease 기간 동안 다른 작업자가 가져가지 않고,
     * 작업자가 죽어 PROCESSING으로 남으면 lease 만료 후 다시 대상이 된다.
     */
    @Transactional
    public List<ClaimedEvent> claim(Instant now, int batchSize, Duration lease) {
        List<Long> ids = outboxEventRepository.lockClaimableIds(now, batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<OutboxEvent> events = outboxEventRepository.findAllById(ids);
        if (events.size() != ids.size()) {
            throw new IllegalStateException("점유한 사건을 찾을 수 없습니다.");
        }
        return events.stream().map(event -> {
            event.lease(now, lease);
            return new ClaimedEvent(event.getId(), event.getAttempts());
        }).toList();
    }

    @Transactional(readOnly = true)
    public Optional<OutboxEvent> find(long eventId) {
        return outboxEventRepository.findById(eventId);
    }

    /**
     * 소비 단위 하나를 처리하고 성공 기록을 같은 트랜잭션에 남긴다.
     * 이미 처리한 단위는 건너뛰므로 사건을 다시 소비해도 결과가 늘지 않는다. (ERD 4.6)
     *
     * @return 이번 호출로 처리했으면 true, 이미 처리돼 건너뛰었으면 false
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deliver(long eventId, OutboxConsumer consumer, String consumerKey, Instant now) {
        if (eventDeliveryRepository.existsByEventIdAndConsumerKey(eventId, consumerKey)) {
            return false;
        }

        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("사건을 찾을 수 없습니다: eventId=" + eventId));

        consumer.consume(event, consumerKey);

        try {
            eventDeliveryRepository.saveAndFlush(EventDelivery.of(eventId, consumerKey, now));
        } catch (DataIntegrityViolationException e) {
            // 다른 작업자가 같은 단위를 먼저 커밋한 경우. 결과 저장도 함께 롤백된다
            log.debug("이미 처리된 소비 단위: eventId={} consumerKey={}", eventId, consumerKey);
            throw new AlreadyDeliveredException(eventId, consumerKey, e);
        }
        return true;
    }

    @Transactional
    public boolean complete(ClaimedEvent claim, Instant now) {
        return outboxEventRepository.findByIdForUpdate(claim.eventId())
                .map(event -> event.complete(claim.attempt(), now))
                .orElse(false);
    }

    @Transactional
    public boolean fail(ClaimedEvent claim, Instant now, Duration retryBackoff, int maxAttempts) {
        return outboxEventRepository.findByIdForUpdate(claim.eventId())
                .map(event -> event.fail(claim.attempt(), now, retryBackoff, maxAttempts))
                .orElse(false);
    }

    /** 점유한 사건과 해당 시도 번호. 이전 작업자의 결과 기록을 막는 기준 */
    public record ClaimedEvent(long eventId, int attempt) {
    }

    /** 경합으로 같은 소비 단위가 중복 커밋되려 한 경우. 사건 전체를 실패로 보지 않는다 */
    public static class AlreadyDeliveredException extends RuntimeException {

        public AlreadyDeliveredException(long eventId, String consumerKey, Throwable cause) {
            super("이미 처리된 소비 단위입니다: eventId=%d consumerKey=%s".formatted(eventId, consumerKey), cause);
        }
    }
}
