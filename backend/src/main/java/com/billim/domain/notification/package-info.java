/**
 * 알림 도메인 — 앱 내 알림과 도메인 사건(outbox). (E 알림·운영)
 *
 * <p>쓰기는 {@link com.billim.domain.notification.service.DomainEventPublisher} 하나로만 들어온다.
 * 다른 도메인은 상태를 바꾼 트랜잭션에서 사건을 적재하고, 알림 행은 커밋 후
 * {@link com.billim.domain.notification.service.OutboxDispatcher}가 만든다.
 *
 * <h2>확장 지점</h2>
 * <ul>
 *   <li>새 사건 유형: {@link com.billim.domain.notification.entity.EventType}에 값 추가</li>
 *   <li>새 알림 문구: {@link com.billim.domain.notification.service.NotificationTemplate} 빈 추가</li>
 *   <li>새 소비 방식(채팅 시스템 메시지 등):
 *       {@link com.billim.domain.notification.service.OutboxConsumer} 빈 추가</li>
 * </ul>
 * 세 경우 모두 디스패처와 컨트롤러는 고치지 않는다.
 *
 * <p>시각은 주입받은 {@link java.time.Clock}으로 읽는다. 현재 이 빈은 물건 도메인의 설정이
 * 제공하므로(ItemClockConfig), 공용 Clock 빈을 {@code global/config}로 옮기는 편이 낫다. 팀 논의 대기.
 */
package com.billim.domain.notification;
