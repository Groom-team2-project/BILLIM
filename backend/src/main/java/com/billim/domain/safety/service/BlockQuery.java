package com.billim.domain.safety.service;

import java.util.Collection;
import java.util.Set;

/**
 * 차단 관계 판정 창구. 안전 도메인이 다른 도메인에 공개하는 읽기 계약이다. (도메인 가이드 C06)
 *
 * <p>판정은 양방향이다. 내가 차단했든 상대가 나를 차단했든 신규 관계는 막는다.
 * 각 도메인은 자기 API에서 이 결과로 노출·전송을 제한한다.
 *
 * <p>연동 지점
 * <ul>
 *   <li>물건: 목록·상세에서 차단 관계 회원의 물건 제외
 *       ({@code ItemAccessPolicy}의 TODO(E) 지점)</li>
 *   <li>채팅: 신규 방 생성·일반 메시지 차단. 단 기존 거래에 연결된 메시지는 허용
 *       (정책 및 상태 5 차단)</li>
 * </ul>
 */
public interface BlockQuery {

    /** 두 회원 사이에 어느 방향이든 차단이 있는지 */
    boolean isBlockedBetween(long memberId, long otherMemberId);

    /**
     * 주어진 회원들 중 나와 차단 관계인 사람. 목록을 걸러낼 때 회원마다 묻지 않도록 한 번에 돌려준다.
     * 후보가 비면 빈 Set.
     */
    Set<Long> findBlockedAmong(long memberId, Collection<Long> candidateMemberIds);
}
