package com.billim.domain.notification.entity;

/** outbox_events.status */
public enum OutboxStatus {
    /** 소비 대기. available_at 이후 획득 대상 */
    PENDING,
    /** 작업자가 획득. lease_until이 지나면 다른 작업자가 재획득 */
    PROCESSING,
    /** 모든 소비자 처리 완료 */
    DONE,
    /** 재시도 한도 소진. 운영에서 원인을 확인한 뒤 다시 PENDING으로 돌린다 */
    FAILED
}
