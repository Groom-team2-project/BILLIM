package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.OutboxEvent;

/**
 * 발행 요청. 상태를 바꾼 도메인이 같은 트랜잭션에서 {@link DomainEventPublisher}에 넘긴다.
 *
 * @param eventKey         사건의 자연 키. 같은 상태 변경을 다시 발행해도 사건은 한 건만 남는다
 * @param eventType        사건 유형
 * @param aggregateId      집합체 ID (대여 ID, 약속 슬롯 ID, 신고 ID)
 * @param aggregateVersion 사건 생성 시 집합체 version
 * @param payload          수신자·이동 대상·표시 정보
 */
public record DomainEvent(
        String eventKey,
        EventType eventType,
        long aggregateId,
        long aggregateVersion,
        EventPayload payload
) {

    public DomainEvent {
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > OutboxEvent.EVENT_KEY_MAX) {
            throw new IllegalArgumentException("event_key 형식이 올바르지 않습니다.");
        }
        if (eventType == null || payload == null) {
            throw new IllegalArgumentException("사건 유형과 payload는 필수입니다.");
        }
        if (aggregateId < 1 || aggregateVersion < 0) {
            throw new IllegalArgumentException("집합체 ID와 version이 올바르지 않습니다.");
        }
    }

    /**
     * 집합체와 version으로 event_key를 조립한다. 예: {@code rental:501:version:2:approved}
     * 같은 집합체의 같은 version에서 같은 사건을 두 번 발행하면 두 번째는 무시된다.
     */
    public static DomainEvent of(EventType eventType, long aggregateId, long aggregateVersion,
                                 EventPayload payload) {
        String eventKey = "%s:%d:version:%d:%s".formatted(
                eventType.aggregateType().slug(), aggregateId, aggregateVersion, eventType.action());
        return new DomainEvent(eventKey, eventType, aggregateId, aggregateVersion, payload);
    }
}
