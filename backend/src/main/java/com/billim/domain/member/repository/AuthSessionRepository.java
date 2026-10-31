package com.billim.domain.member.repository;

import com.billim.domain.member.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    /** 요청마다 쿠키 해시로 세션 복원. member를 함께 읽어 추가 조회 방지 */
    @Query("""
            select s from AuthSession s
            left join fetch s.member
            where s.tokenHash = :tokenHash
            """)
    Optional<AuthSession> findWithMember(String tokenHash);

    /**
     * 회원의 현재 세션 외 사용 가능한 세션 일괄 폐기. 반환값은 폐기 건수.
     * 일괄 UPDATE라 감사 리스너 미동작 — updated_at 직접 갱신.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AuthSession s
            set s.revokedAt = :now, s.updatedAt = :now
            where s.member.id = :memberId
              and s.id <> :currentSessionId
              and s.revokedAt is null
              and s.expiresAt > :now
            """)
    int revokeOthers(@Param("memberId") Long memberId,
                     @Param("currentSessionId") Long currentSessionId,
                     @Param("now") Instant now);

    /**
     * 절대 만료가 지난 세션을 묶음 단위로 삭제. 참조하는 oauth_login_attempts를 먼저 삭제 필요.
     * 묶음마다 별도 트랜잭션 — 전부 한 번에 지우면 커밋까지 잠금이 유지되어 인증 응답시간 상승.
     * LIMIT은 JPQL에 없어 네이티브 쿼리.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = "delete from auth_sessions where absolute_expires_at < :threshold limit :chunk",
            nativeQuery = true)
    int deleteExpiredBefore(@Param("threshold") Instant threshold, @Param("chunk") int chunk);
}
