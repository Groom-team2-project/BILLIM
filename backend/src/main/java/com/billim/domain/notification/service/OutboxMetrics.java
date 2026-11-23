package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.OutboxStatus;
import com.billim.domain.notification.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 사건 적재량 지표. 알림 누락을 사용자 신고 전에 알아채기 위한 최소 관측이다. (기획서 E 어필)
 *
 * <p>{@code billim_outbox_events{status="PENDING"}}가 계속 쌓이면 소비가 밀린 것이고,
 * {@code status="FAILED"}가 0을 넘으면 재시도 한도를 소진한 사건이 있다는 뜻이다.
 * 값은 수집 시점에 COUNT로 읽으므로 상태별 인덱스(status, available_at, id)를 탄다.
 */
@Component
public class OutboxMetrics {

    private static final String NAME = "billim.outbox.events";

    public OutboxMetrics(MeterRegistry registry, OutboxEventRepository outboxEventRepository) {
        for (OutboxStatus status : OutboxStatus.values()) {
            Gauge.builder(NAME, () -> outboxEventRepository.countByStatus(status))
                    .tag("status", status.name())
                    .description("상태별 도메인 사건 수")
                    .register(registry);
        }
    }
}
