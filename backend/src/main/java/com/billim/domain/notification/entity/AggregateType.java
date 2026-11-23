package com.billim.domain.notification.entity;

import java.util.Locale;

/** outbox_events.aggregate_type. aggregate_id는 이 종류 기준의 논리 참조다 (다형 FK 없음) */
public enum AggregateType {
    RENTAL,
    APPOINTMENT_SLOT,
    REPORT;

    /** event_key 첫 조각. 예: <b>rental</b>:501:version:2:approved */
    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }
}
