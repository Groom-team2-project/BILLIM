package com.billim.domain.member.service;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.AuthSessionRepository;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세션 수명 규칙을 실제 MySQL에서 확인.
 * 시간 경과가 필요해 Clock을 직접 조작. Docker 필요.
 */
@DataJpaTest(properties = {
        "billim.auth.session-idle-ttl=2h",
        "billim.auth.session-absolute-ttl=24h",
        "billim.auth.session-touch-interval=5m",
})
@Import({AuthSessionServiceTest.TestSupport.class, AuthSessionService.class, JpaAuditingConfig.class})
class AuthSessionServiceTest {

    private static final Instant START = Instant.parse("2026-10-07T00:00:00Z");
    private static final MutableClock CLOCK = new MutableClock(START);

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSupport {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }

        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    @Autowired AuthSessionService authSessionService;
    @Autowired AuthSessionRepository authSessionRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;

    @BeforeEach
    void resetClock() {
        CLOCK.set(START);
    }

    @Test
    @DisplayName("발급 시 쿠키 원문이 아니라 해시가 저장된다")
    void storesHashNotRawToken() {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous("csrf-value");

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.session().getTokenHash())
                .isNotEqualTo(issued.token())
                .hasSize(64);
        assertThat(issued.session().getCsrfToken()).isEqualTo("csrf-value");
        assertThat(issued.session().isLoggedIn()).isFalse();
    }

    @Test
    @DisplayName("발급한 토큰으로 세션을 복원한다")
    void loadsSessionByToken() {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous("csrf-value");

        assertThat(authSessionService.loadUsable(issued.token())).isPresent();
        assertThat(authSessionService.loadUsable("다른-토큰")).isEmpty();
    }

    @Test
    @DisplayName("로그인 시 기존 세션을 폐기하고 새 토큰으로 회전한다")
    void rotatesSessionOnLogin() {
        Member member = memberRepository.save(Member.register("홍길동"));
        AuthSessionService.Issued before = authSessionService.issueAnonymous("csrf-value");

        AuthSessionService.Issued after = authSessionService.rotateForMember(before.token(), member.getId());
        em.flush();

        assertThat(after.token()).isNotEqualTo(before.token());
        assertThat(after.session().isLoggedIn()).isTrue();
        assertThat(authSessionService.loadUsable(before.token())).isEmpty();
        assertThat(authSessionService.loadUsable(after.token())).isPresent();
    }

    @Test
    @DisplayName("로그아웃하면 폐기 시각이 남고 복원되지 않는다")
    void revokeMarksSessionUnusable() {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous("csrf-value");

        authSessionService.revoke(issued.token());
        em.flush();

        assertThat(authSessionService.loadUsable(issued.token())).isEmpty();
        assertThat(authSessionRepository.findById(issued.session().getId()))
                .get()
                .extracting(AuthSession::getRevokedAt)
                .isNotNull();
    }

    @Test
    @DisplayName("유휴 만료가 지나면 복원되지 않는다")
    void expiresAfterIdleTtl() {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous("csrf-value");

        CLOCK.advance(Duration.ofHours(2).plusMinutes(1));

        assertThat(authSessionService.loadUsable(issued.token())).isEmpty();
    }

    @Test
    @DisplayName("갱신 주기 안에서는 last_seen_at을 쓰지 않는다")
    void skipsTouchWithinInterval() {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous("csrf-value");
        Instant lastSeen = issued.session().getLastSeenAt();

        CLOCK.advance(Duration.ofMinutes(4));
        authSessionService.loadUsable(issued.token());
        em.flush();

        assertThat(reload(issued).getLastSeenAt()).isEqualTo(lastSeen);
    }

    @Test
    @DisplayName("갱신 주기를 넘기면 유휴 만료가 연장된다")
    void touchesAfterInterval() {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous("csrf-value");
        Instant expiresBefore = issued.session().getExpiresAt();

        CLOCK.advance(Duration.ofMinutes(6));
        authSessionService.loadUsable(issued.token());
        em.flush();

        assertThat(reload(issued).getExpiresAt()).isAfter(expiresBefore);
    }

    @Test
    @DisplayName("연장해도 절대 만료는 넘지 않는다")
    void neverExtendsBeyondAbsoluteExpiry() {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous("csrf-value");
        Instant absolute = issued.session().getAbsoluteExpiresAt();

        // 절대 만료 1시간 전. 유휴 2시간을 그대로 더하면 절대 만료를 넘는 지점
        CLOCK.advance(Duration.ofHours(23));
        authSessionService.loadUsable(issued.token());
        em.flush();

        assertThat(reload(issued).getExpiresAt()).isBeforeOrEqualTo(absolute);
    }

    private AuthSession reload(AuthSessionService.Issued issued) {
        em.clear();
        return authSessionRepository.findById(issued.session().getId()).orElseThrow();
    }

    /** 경과 시간을 직접 조작하는 테스트용 Clock */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant value) {
            this.instant = value;
        }

        void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
