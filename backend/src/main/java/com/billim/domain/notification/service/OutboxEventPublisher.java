package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 사건 적재 구현. 호출 측 트랜잭션에 반드시 참여한다({@link Propagation#MANDATORY}).
 * 트랜잭션 없이 호출하면 상태 변경과 사건이 따로 커밋될 수 있어 예외로 막는다.
 */
@Service
public class OutboxEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final EventPayloadCodec payloadCodec;
    private final Clock clock;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository,
                                EventPayloadCodec payloadCodec, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.payloadCodec = payloadCodec;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean publish(DomainEvent event) {
        // 같은 상태 변경을 재시도해도 사건은 한 건만 남긴다.
        // 집합체 행 잠금 아래에서 호출하는 것이 전제이고, 경합이 나면 event_key UNIQUE가 최종 방어선이다.
        if (outboxEventRepository.existsByEventKey(event.eventKey())) {
            log.debug("이미 적재된 사건이라 건너뜀: eventKey={}", event.eventKey());
            return false;
        }

        if (event.payload().recipients().isEmpty()) {
            // 수신자가 없는 사건도 적재한다. 시스템 메시지 등 다른 소비자가 쓸 수 있다
            log.debug("수신자 없는 사건 적재: eventKey={}", event.eventKey());
        }

        outboxEventRepository.save(OutboxEvent.pending(
                event.eventKey(),
                event.eventType(),
                event.aggregateId(),
                event.aggregateVersion(),
                payloadCodec.write(event.payload()),
                clock.instant()));

        log.info("사건 적재: eventKey={} type={}", event.eventKey(), event.eventType());
        return true;
    }
}
