package com.billim.domain.member.repository;

import com.billim.domain.member.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

    /** 절대 만료가 지난 세션 삭제. 참조하는 oauth_login_attempts를 먼저 지워야 한다 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from AuthSession s where s.absoluteExpiresAt < :threshold")
    int deleteExpiredBefore(Instant threshold);
}
