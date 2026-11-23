package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.OutboxEvent;

import java.util.List;

/**
 * 사건 소비자. 새 소비 방식을 추가할 때 이 인터페이스를 구현한 빈을 등록하면
 * {@code OutboxDispatcher}가 자동으로 함께 호출한다. (예: 채팅 시스템 메시지 소비자)
 *
 * <p>중복 방지는 디스패처가 담당한다. 디스패처는 소비 단위({@code consumerKey})마다
 * {@code event_deliveries} 기록을 확인해 처리하지 않은 단위만 {@link #consume}에 넘기고,
 * 결과 저장과 성공 기록을 한 트랜잭션에서 커밋한다. 구현체는 중복 검사를 다시 하지 않아도 된다.
 */
public interface OutboxConsumer {

    /**
     * 소비 단위 키 목록. {@code event_deliveries.consumer_key} 형식을 따른다.
     * 예: 알림은 {@code notification:{memberId}}, 채팅 시스템 메시지는 {@code room:{roomId}}.
     * 이 사건에서 처리할 것이 없으면 빈 목록을 반환한다.
     */
    List<String> consumerKeys(OutboxEvent event);

    /**
     * 소비 단위 하나를 처리한다. 디스패처의 트랜잭션 안에서 호출되므로 예외를 던지면
     * 이 단위의 결과와 성공 기록이 함께 롤백되고 사건은 재시도 대상으로 남는다.
     */
    void consume(OutboxEvent event, String consumerKey);
}
