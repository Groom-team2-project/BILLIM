package com.billim.domain.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 사건 소비 주기 실행. 테스트·부하 측정에서 끌 수 있게 설정으로 분리한다.
 * ({@code billim.notification.outbox.enabled=false})
 *
 * <p>fixedDelay라 이전 실행이 끝난 뒤 다음 실행이 시작된다. 인스턴스가 여러 대여도
 * 획득 쿼리의 SKIP LOCKED가 같은 사건의 중복 처리를 막는다.
 */
@Component
@ConditionalOnProperty(name = "billim.notification.outbox.enabled", matchIfMissing = true)
public class OutboxDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchScheduler.class);

    private final OutboxDispatcher dispatcher;

    public OutboxDispatchScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(
            initialDelayString = "${billim.notification.outbox.initial-delay:5s}",
            fixedDelayString = "${billim.notification.outbox.poll-interval:1s}")
    public void dispatch() {
        try {
            int completed = dispatcher.dispatchBatch();
            if (completed > 0) {
                log.debug("사건 소비 완료: {}건", completed);
            }
        } catch (RuntimeException e) {
            // 주기 실행이 멈추지 않게 여기서 멈춘다. 개별 사건 실패는 디스패처가 이미 처리했다
            log.error("사건 소비 주기 실행 실패", e);
        }
    }
}
