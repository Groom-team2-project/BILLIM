package com.billim.domain.notification.service;

/**
 * 도메인 사건 발행 창구. 알림 도메인이 외부에 공개하는 유일한 쓰기 진입점이다.
 *
 * <p>호출 규칙
 * <ul>
 *   <li>상태를 바꾼 트랜잭션 안에서 호출한다. 사건과 상태 변경이 함께 커밋되거나 함께 롤백된다.</li>
 *   <li>알림 행은 커밋 후 {@code OutboxDispatcher}가 만든다. 호출 측은 알림 생성을 기다리지 않는다.</li>
 *   <li>집합체 행 잠금 아래에서 호출한다. 같은 사건의 동시 발행은 event_key UNIQUE가 막는다.</li>
 * </ul>
 */
public interface DomainEventPublisher {

    /**
     * 사건을 적재한다. 같은 event_key가 이미 있으면 아무것도 하지 않는다.
     *
     * @return 이번 호출로 새로 적재했으면 true, 이미 있어 건너뛰었으면 false
     */
    boolean publish(DomainEvent event);
}
