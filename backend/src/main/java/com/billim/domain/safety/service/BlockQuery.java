package com.billim.domain.safety.service;

import java.util.Collection;
import java.util.Set;

/**
 * 차단 관계 판정 창구. 안전 도메인이 다른 도메인에 공개하는 읽기 계약이다. (도메인 가이드 C06)
 *
 * <h2>양방향과 단방향을 구분한다</h2>
 * <ul>
 *   <li><b>접근 제한</b>에는 양방향({@link #isBlockedBetween})을 쓴다. 내가 차단했든 상대가 나를
 *       차단했든 신규 관계는 막아야 한다.</li>
 *   <li><b>화면 표시</b>에는 단방향({@link #hasBlocked})을 쓴다. 차단·해제 버튼은 내가 만든
 *       방향만 바꿀 수 있으므로, 상대가 나를 차단한 사실로 내 버튼 상태를 정하면 안 된다.</li>
 * </ul>
 *
 * <p>각 도메인은 자기 API에서 이 결과로 노출·전송을 제한한다.
 *
 * <p>연동 지점
 * <ul>
 *   <li>물건: 목록·상세에서 차단 관계 회원의 물건 제외 — 양방향
 *       ({@code ItemAccessPolicy}의 TODO(E) 지점)</li>
 *   <li>채팅: 신규 방 생성·일반 메시지 차단 — 양방향. 단 기존 거래에 연결된 메시지는 허용
 *       (정책 및 상태 5 차단)</li>
 *   <li>회원: 이웃 공개 프로필의 {@code blockedByMe} — 단방향 (API 명세 PublicProfile)</li>
 * </ul>
 */
public interface BlockQuery {

    /** 두 회원 사이에 어느 방향이든 차단이 있는지. 접근·전송 제한 판정용 */
    boolean isBlockedBetween(long memberId, long otherMemberId);

    /**
     * 내가 상대를 차단했는지. 내가 만든 방향만 본다.
     *
     * <p>프로필의 차단·해제 버튼 상태(API 명세 {@code PublicProfile.blockedByMe})에 쓴다.
     * 상대가 나를 차단한 경우는 false다 — 그 관계는 내가 해제할 수 없기 때문이다.
     * 접근을 막을지 판단할 때는 {@link #isBlockedBetween}을 써야 한다.
     */
    boolean hasBlocked(long memberId, long targetMemberId);

    /**
     * 주어진 회원들 중 나와 차단 관계인 사람. 목록을 걸러낼 때 회원마다 묻지 않도록 한 번에 돌려준다.
     * 양방향 판정이며, 후보가 비면 빈 Set.
     */
    Set<Long> findBlockedAmong(long memberId, Collection<Long> candidateMemberIds);
}
