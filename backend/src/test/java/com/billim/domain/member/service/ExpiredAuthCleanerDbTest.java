package com.billim.domain.member.service;

import com.billim.domain.member.repository.AuthSessionRepository;
import com.billim.domain.member.repository.OauthLoginAttemptRepository;
import com.billim.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 만료 정리의 묶음 삭제. 한 트랜잭션에 전부 지우면 정리 중 인증 응답시간 상승.
 * 묶음 크기를 2로 두고 그보다 많은 행이 모두 지워지는지 확인. Docker 필요
 */
@DataJpaTest
@Import({ExpiredAuthCleanerDbTest.MySql.class, ExpiredAuthCleaner.class, JpaAuditingConfig.class})
@TestPropertySource(properties = {
        "billim.auth.cleanup-cron=0 0 4 1 1 *",      // 테스트 중 자동 실행 방지
        "billim.auth.cleanup-retention=7d",
        "billim.auth.cleanup-chunk-size=2",
        "billim.auth.cleanup-chunk-pause-ms=0"
})
class ExpiredAuthCleanerDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired ExpiredAuthCleaner cleaner;
    @Autowired AuthSessionRepository authSessionRepository;
    @Autowired OauthLoginAttemptRepository oauthLoginAttemptRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("묶음 크기보다 많아도 만료 세션을 전부 지운다")
    void purgesAllExpiredAcrossChunks() {
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        for (int i = 0; i < 5; i++) {
            insertSession("expired-" + i, old);
        }
        Instant alive = Instant.now().plus(30, ChronoUnit.DAYS);
        insertSession("alive", alive);
        em.flush();
        em.clear();

        cleaner.purgeExpired();

        assertThat(authSessionRepository.count()).isEqualTo(1);
        assertThat(authSessionRepository.findWithMember(hash("alive"))).isPresent();
    }

    /** 묶음이 실제로 쪼개지는지 — LIMIT이 빠지면 한 번에 전부 삭제되어 이 단정이 깨짐 */
    @Test
    @DisplayName("한 묶음은 지정한 수만큼만 지운다")
    void deletesOnlyChunkSizePerCall() {
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        for (int i = 0; i < 5; i++) {
            insertSession("chunk-" + i, old);
        }
        em.flush();
        em.clear();

        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);

        assertThat(authSessionRepository.deleteExpiredBefore(threshold, 2)).isEqualTo(2);
        assertThat(authSessionRepository.count()).isEqualTo(3);
        assertThat(authSessionRepository.deleteExpiredBefore(threshold, 10)).isEqualTo(3);
        assertThat(authSessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("만료 전 세션은 남긴다")
    void keepsLiveSessions() {
        insertSession("a", Instant.now().plus(1, ChronoUnit.DAYS));
        insertSession("b", Instant.now().plus(2, ChronoUnit.DAYS));
        em.flush();
        em.clear();

        cleaner.purgeExpired();

        assertThat(authSessionRepository.count()).isEqualTo(2);
    }

    /**
     * 삭제 순서 — oauth_login_attempts가 auth_sessions를 FK(RESTRICT)로 참조.
     * 세션을 먼저 지우면 제약 위반
     */
    @Test
    @DisplayName("세션을 참조하는 OAuth 시도가 있어도 순서대로 지운다")
    void purgesAttemptsBeforeSessions() {
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        insertSession("with-attempt", old);
        em.flush();
        Long sessionId = (Long) em.createNativeQuery(
                        "select id from auth_sessions where token_hash = :hash")
                .setParameter("hash", hash("with-attempt"))
                .getSingleResult();
        insertAttempt(sessionId, old);
        em.flush();
        em.clear();

        cleaner.purgeExpired();

        assertThat(oauthLoginAttemptRepository.count()).isZero();
        assertThat(authSessionRepository.count()).isZero();
    }

    /** 보관 기간 경계 — 임계값보다 앞선 것만 삭제 대상 */
    @Test
    @DisplayName("보관 기간이 지나지 않은 만료 세션은 남긴다")
    void keepsRecentlyExpired() {
        insertSession("recent", Instant.now().minus(1, ChronoUnit.DAYS));
        em.flush();
        em.clear();

        cleaner.purgeExpired();

        assertThat(authSessionRepository.count()).isEqualTo(1);
    }

    /** 0이면 LIMIT 0 삭제가 종료 조건에 걸리지 않아 정리가 조용히 꺼짐 — 기동 시점 거부 */
    @Test
    @DisplayName("묶음 크기 0은 생성 시점에 거부한다")
    void rejectsZeroChunkSize() {
        assertThatThrownBy(() -> new ExpiredAuthCleaner(
                authSessionRepository, oauthLoginAttemptRepository, Clock.systemUTC(),
                Duration.ofDays(7), 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 인터럽트 수신 후에도 삭제가 계속되면 셧다운이 배치에 붙잡힘 */
    @Test
    @DisplayName("인터럽트를 받으면 남은 묶음 정리를 중단한다")
    void stopsPurgeOnInterrupt() {
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        for (int i = 0; i < 5; i++) {
            insertSession("int-" + i, old);
        }
        em.flush();
        em.clear();

        Thread.currentThread().interrupt();
        boolean interruptPreserved;
        try {
            cleaner.purgeExpired();
        } finally {
            interruptPreserved = Thread.interrupted();   // 확인과 동시에 해제 — 플래그가 남으면 이후 테스트 오염
        }

        assertThat(interruptPreserved).isTrue();
        assertThat(authSessionRepository.count()).isEqualTo(5);
    }

    private void insertSession(String token, Instant absoluteExpiresAt) {
        em.createNativeQuery("""
                        insert into auth_sessions
                            (member_id, token_hash, csrf_token, last_seen_at, expires_at,
                             absolute_expires_at, created_at, updated_at)
                        values (null, :hash, 'test', :now, :expires, :absolute, :now, :now)
                        """)
                .setParameter("hash", hash(token))
                .setParameter("now", Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime())
                .setParameter("expires", absoluteExpiresAt.atOffset(ZoneOffset.UTC).toLocalDateTime())
                .setParameter("absolute", absoluteExpiresAt.atOffset(ZoneOffset.UTC).toLocalDateTime())
                .executeUpdate();
    }

    private void insertAttempt(long sessionId, Instant expiresAt) {
        em.createNativeQuery("""
                        insert into oauth_login_attempts
                            (session_id, state_hash, provider, redirect_uri, expires_at, created_at, updated_at)
                        values (:sessionId, :hash, 'KAKAO', 'http://localhost/cb', :expires, :now, :now)
                        """)
                .setParameter("sessionId", sessionId)
                .setParameter("hash", hash("state"))
                .setParameter("expires", expiresAt.atOffset(ZoneOffset.UTC).toLocalDateTime())
                .setParameter("now", Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime())
                .executeUpdate();
    }

    /** token_hash는 char(64) — 길이를 맞춘 더미 */
    private static String hash(String token) {
        return String.format("%-64s", token).replace(' ', '0');
    }
}
