package com.billim.domain.item.port;

import java.util.Set;

/**
 * 차단·제재 판정 계약. 구현은 안전 담당(E) 영역.
 * TODO(E): blocks 기반 구현체 연결.
 */
public interface BlockPort {

    /** 나와 차단 관계(양방향)인 회원 ID. 이 회원들의 물건은 검색·상세에서 제외 */
    Set<Long> findBlockRelatedMemberIds(long memberId);
}
