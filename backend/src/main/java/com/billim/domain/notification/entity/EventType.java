package com.billim.domain.notification.entity;

/**
 * outbox_events.event_type. 사건 종류를 늘릴 때 여기에 값을 추가하고
 * {@link com.billim.domain.notification.service.NotificationTemplate} 구현에 문구를 등록한다.
 * 저장 컬럼은 VARCHAR(50)이므로 이름 길이를 넘기지 않는다.
 */
public enum EventType {

    // 대여 (C 대여·반납)
    RENTAL_REQUESTED(AggregateType.RENTAL, "requested"),
    RENTAL_APPROVED(AggregateType.RENTAL, "approved"),
    RENTAL_REJECTED(AggregateType.RENTAL, "rejected"),
    RENTAL_CANCELED(AggregateType.RENTAL, "canceled"),
    RENTAL_HANDED_OVER(AggregateType.RENTAL, "handed_over"),
    RENTAL_RETURNED(AggregateType.RENTAL, "returned"),
    RENTAL_EXPIRED(AggregateType.RENTAL, "expired"),

    // 전달·반납 약속
    APPOINTMENT_PROPOSED(AggregateType.APPOINTMENT_SLOT, "proposed"),
    APPOINTMENT_CONFIRMED(AggregateType.APPOINTMENT_SLOT, "confirmed"),
    APPOINTMENT_WITHDRAWN(AggregateType.APPOINTMENT_SLOT, "withdrawn"),

    // 신고 처리 (E 안전·운영)
    REPORT_RESOLVED(AggregateType.REPORT, "resolved");

    private final AggregateType aggregateType;
    private final String action;

    EventType(AggregateType aggregateType, String action) {
        this.aggregateType = aggregateType;
        this.action = action;
    }

    /** 사건이 속한 집합체 종류. 발행 측이 따로 지정하지 않게 사건 유형이 고정한다 */
    public AggregateType aggregateType() {
        return aggregateType;
    }

    /** event_key 마지막 조각. 예: rental:501:version:2:<b>approved</b> (ERD 4.6) */
    public String action() {
        return action;
    }
}
