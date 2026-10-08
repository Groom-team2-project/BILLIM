package com.billim.domain.member.repository;

import com.billim.domain.member.entity.OauthLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface OauthLoginAttemptRepository extends JpaRepository<OauthLoginAttempt, Long> {

    /** 콜백 검증용. 시작 당시 세션을 함께 읽어 브라우저 일치 확인 */
    @Query("""
            select a from OauthLoginAttempt a
            join fetch a.session
            where a.stateHash = :stateHash
            """)
    Optional<OauthLoginAttempt> findWithSession(String stateHash);

    /**
     * state 1회 소비. 반환 1이면 이 요청이 소비에 성공한 유일한 요청.
     * 조회 후 저장 방식은 동시 요청이 함께 통과할 수 있어 단일 UPDATE로 처리.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OauthLoginAttempt a set a.consumedAt = :now, a.updatedAt = :now
            where a.stateHash = :stateHash and a.session.id = :sessionId
              and a.consumedAt is null and a.expiresAt > :now
            """)
    int consumeOnce(String stateHash, Long sessionId, Instant now);
}
