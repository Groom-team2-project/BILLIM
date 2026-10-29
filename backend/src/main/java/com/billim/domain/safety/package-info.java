/**
 * 안전·운영 도메인 — 차단·신고·제재·공지·운영 통계. (E 알림·운영)
 *
 * <p>현재 구현 범위는 신고 접수·조회와 관리자 처리·제재, 동네 운영 통계다
 * (API 명세 12.4 ~ 13.6). 차단(blocks)과 공지(notices)는 아직 없다.
 *
 * <h2>도메인 경계</h2>
 * <ul>
 *   <li>운영 통계는 물건·대여·소속·신고를 함께 세므로
 *       {@link com.billim.domain.safety.repository.AdminStatisticsRepository}가 다른 도메인의
 *       테이블을 네이티브 쿼리로 직접 읽는다. 조회 전용이고 엔티티를 쓰지 않아 각 도메인의
 *       쓰기 규칙에는 관여하지 않는다. 집계 정의는 도메인 가이드 결정 '운영 통계'에 있다.</li>
 *   <li>신고 대상 해석과 접근 권한은 {@link com.billim.domain.safety.service.ReportTargetResolver}에
 *       모아 둔다. 물건·메시지 조회 규칙이 바뀌면 이 한 곳만 고친다.</li>
 *   <li>제재는 이 도메인이 결정·보관하고, 제재 상태로 요청을 막는 적용은 인증·회원 도메인이 한다.
 *       그 입구가 {@link com.billim.domain.safety.service.SanctionService#isRestricted(long)}다.
 *       (ERD 4.7 — E 결정 / A 적용)</li>
 *   <li>처리 결과 알림은 직접 만들지 않고 알림 도메인의
 *       {@link com.billim.domain.notification.service.DomainEventPublisher}로 사건만 발행한다.
 *       처리·제재·사건이 한 트랜잭션에서 커밋된다.</li>
 * </ul>
 *
 * <h2>노출 범위</h2>
 * 신고자 정보와 internalNote는 관리자 응답에만 담는다. 제재 대상에게는 조치 종류만 알리고
 * 누가 신고했는지, 관리자가 쓴 사유 원문은 알리지 않는다. (API 명세 12.6, ERD 4.6)
 */
package com.billim.domain.safety;
