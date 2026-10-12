package com.billim.domain.notification.service;

import com.billim.domain.notification.entity.EventType;

/**
 * 사건 → 알림 문구. 새 사건 유형의 문구는 이 인터페이스를 구현한 빈을 추가해 등록한다.
 * {@code NotificationTemplates}가 {@link #supports}로 담당 템플릿을 찾는다.
 *
 * <p>문구는 생성 시점의 스냅샷이다. 원본이 바뀌어도 이미 만든 알림은 고치지 않으므로
 * {@link EventPayload.Display}에 담긴 값만 쓰고 다른 도메인을 다시 조회하지 않는다.
 */
public interface NotificationTemplate {

    boolean supports(EventType eventType);

    /**
     * 수신자에게 보여줄 한 줄 문구. (API 명세 Notification.title, 최대 200자)
     *
     * @param recipient 수신자와 역할. 같은 사건도 역할에 따라 문구가 다르다
     */
    String title(EventType eventType, EventPayload payload, EventPayload.Recipient recipient);
}
