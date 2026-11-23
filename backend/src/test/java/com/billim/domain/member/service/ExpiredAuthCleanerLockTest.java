package com.billim.domain.member.service;

import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.config.SchedulingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 만료 정리의 인스턴스 간 잠금. 다른 인스턴스는 shedlock 행으로 대신함.
 * 잠금 판정이 실제 커밋된 행 기준이라 테스트 트랜잭션 미사용. Docker 필요
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ExpiredAuthCleanerLockTest.MySql.class, ExpiredAuthCleaner.class,
        SchedulingConfig.class, JpaAuditingConfig.class})
@TestPropertySource(properties = {
        "billim.auth.cleanup-cron=0 0 4 1 1 *",      // 테스트 중 자동 실행 방지
        "billim.auth.cleanup-retention=7d",
        "billim.auth.cleanup-chunk-size=500",
        "billim.auth.cleanup-chunk-pause-ms=0"
})
class ExpiredAuthCleanerLockTest {

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
    @Autowired DataSource dataSource;

    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from oauth_login_attempts");
        jdbc.update("delete from auth_sessions");
        // 행 삭제 금지 — ShedLock이 삽입한 행을 기억해 이후 UPDATE만 시도, 행이 없으면 잠금 획득 실패
        holdLock("utc_timestamp(3) - interval 1 second", "previous-test");
    }

    @Test
    @DisplayName("다른 인스턴스가 잠금을 잡고 있으면 정리를 건너뛴다")
    void skipsWhileOtherInstanceHoldsLock() {
        holdLock("utc_timestamp(3) + interval 10 minute", "other-host");
        insertExpiredSession("expired");

        cleaner.purgeExpired();

        assertThat(sessionCount()).isEqualTo(1);
    }

    /** cron 발화 시각이 인스턴스마다 어긋나도 먼저 끝난 정리 직후 재실행 없음 */
    @Test
    @DisplayName("정리를 마친 뒤에도 최소 유지 시간 동안 재실행하지 않는다")
    void holdsLockAfterFinishing() {
        insertExpiredSession("first");

        cleaner.purgeExpired();

        assertThat(sessionCount()).isZero();
        insertExpiredSession("second");

        cleaner.purgeExpired();

        assertThat(sessionCount()).isEqualTo(1);
    }

    /** 정리 중 종료된 인스턴스의 잠금이 영구히 남지 않음 */
    @Test
    @DisplayName("잠금 유지 시각이 지나면 다시 정리한다")
    void runsAfterLockExpires() {
        holdLock("utc_timestamp(3) - interval 1 second", "dead-host");
        insertExpiredSession("expired");

        cleaner.purgeExpired();

        assertThat(sessionCount()).isZero();
    }

    private void holdLock(String lockUntilSql, String lockedBy) {
        jdbc.update("replace into shedlock (name, lock_until, locked_at, locked_by) values ('auth-expired-cleanup', "
                + lockUntilSql + ", utc_timestamp(3), ?)", lockedBy);
    }

    private void insertExpiredSession(String token) {
        var old = Instant.now().minus(30, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC).toLocalDateTime();
        var now = Instant.now().atOffset(ZoneOffset.UTC).toLocalDateTime();
        jdbc.update("""
                insert into auth_sessions
                    (member_id, token_hash, csrf_token, last_seen_at, expires_at,
                     absolute_expires_at, created_at, updated_at)
                values (null, sha2(?, 256), 'test', ?, ?, ?, ?, ?)
                """, token, now, old, old, now, now);
    }

    private int sessionCount() {
        return jdbc.queryForObject("select count(*) from auth_sessions", Integer.class);
    }
}
