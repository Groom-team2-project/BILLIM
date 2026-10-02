package com.billim.domain.item.port;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * 회원 표시 정보 조회 계약. 구현은 회원 담당(A) 영역.
 * TODO(A): members 기반 구현체 연결.
 */
public interface MemberPort {

    /** 회원 ID → 요약. 없는 ID는 결과에서 빠진다. */
    Map<Long, MemberSummary> findSummaries(Collection<Long> memberIds);

    record MemberSummary(long id, String displayName, Instant joinedAt) {
    }
}
