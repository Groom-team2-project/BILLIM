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
 * 조치 정보는 보는 사람에 따라 세 갈래다. (도메인 가이드 결정 '조치 사유를 누구에게 보여줄지')
 * <ul>
 *   <li><b>신고자</b>: 조치 종류와 publicReason. (E_056·E_057)</li>
 *   <li><b>조치 대상 본인</b>: 조치 종류와 publicReason, 제재 기간·해제 여부. (E_072)</li>
 *   <li><b>관리자</b>: 전부. 신고자 정보와 internalNote 포함. (E_058·E_059)</li>
 * </ul>
 * publicReason은 이름 그대로 <b>공개 사유</b>다. 운영자는 신고자와 대상 양쪽이 읽는다는 전제로
 * 쓴다. 양쪽에 숨기는 것은 internalNote이고, 대상에게만 추가로 숨기는 것은 신고자 정보,
 * 신고 사유 코드, 신고 대상 거래·메시지다 — 거래를 특정하면 신고자가 사실상 드러난다.
 * (API 명세 12.6·12.7, ERD 4.6)
 *
 * <p>알림 문구에는 조치 종류만 담는다. 잠금화면에 사유가 드러나지 않게 하고, 자세한 내용은
 * E_072로 확인하게 한다.
 */
package com.billim.domain.safety;
