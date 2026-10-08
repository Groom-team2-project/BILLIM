package com.billim.domain.member.service;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.OauthLoginAttempt;
import com.billim.domain.member.repository.AuthSessionRepository;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.security.session.SessionTokens;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * state 1회 소비 규칙을 실제 MySQL에서 확인.
 * 재사용과 만료가 차단되지 않으면 인가 코드 재생 공격이 성립. Docker 필요.
 */
@DataJpaTest(properties = "billim.auth.oauth-state-ttl=10m")
@Import({OauthLoginAttemptServiceTest.TestSupport.class, OauthLoginAttemptService.class, JpaAuditingConfig.class})
class OauthLoginAttemptServiceTest {

    private static final Instant START = Instant.parse("2026-10-08T00:00:00Z");
    private static final MutableClock CLOCK = new MutableClock(START);
    private static final String REDIRECT_URI = "http://localhost:8080/api/v1/auth/kakao/callback";

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

    @Autowired OauthLoginAttemptService oauthLoginAttemptService;
    @Autowired AuthSessionRepository authSessionRepository;
    @Autowired EntityManager em;

    @BeforeEach
    void resetClock() {
        CLOCK.set(START);
    }

    @Test
    @DisplayName("state는 원문이 아니라 해시로 저장")
    void storesStateHashOnly() {
        AuthSession session = givenSession();

        oauthLoginAttemptService.start(session, "state-value", "verifier", REDIRECT_URI);
        em.flush();

        OauthLoginAttempt saved = oauthLoginAttemptService.consume("state-value");
        assertThat(saved.getStateHash())
                .isNotEqualTo("state-value")
                .isEqualTo(SessionTokens.hash("state-value"));
    }

    @Test
    @DisplayName("소비에 성공하면 시작 당시 세션과 PKCE 값을 돌려받는다")
    void consumeReturnsStoredContext() {
        AuthSession session = givenSession();
        oauthLoginAttemptService.start(session, "state-value", "verifier", REDIRECT_URI);
        em.flush();

        OauthLoginAttempt consumed = oauthLoginAttemptService.consume("state-value");

        assertThat(consumed).isNotNull();
        assertThat(consumed.getSession().getId()).isEqualTo(session.getId());
        assertThat(consumed.getCodeVerifier()).isEqualTo("verifier");
        assertThat(consumed.getRedirectUri()).isEqualTo(REDIRECT_URI);
    }

    /** 재사용이 통과하면 가로챈 인가 코드로 반복 로그인이 가능 */
    @Test
    @DisplayName("같은 state는 두 번 소비되지 않는다")
    void consumesOnlyOnce() {
        AuthSession session = givenSession();
        oauthLoginAttemptService.start(session, "state-value", "verifier", REDIRECT_URI);
        em.flush();

        assertThat(oauthLoginAttemptService.consume("state-value")).isNotNull();
        assertThat(oauthLoginAttemptService.consume("state-value")).isNull();
    }

    @Test
    @DisplayName("저장되지 않은 state는 소비되지 않는다")
    void rejectsUnknownState() {
        assertThat(oauthLoginAttemptService.consume("없는-state")).isNull();
    }

    @Test
    @DisplayName("유효 시간이 지난 state는 소비되지 않는다")
    void rejectsExpiredState() {
        AuthSession session = givenSession();
        oauthLoginAttemptService.start(session, "state-value", "verifier", REDIRECT_URI);
        em.flush();

        CLOCK.advance(Duration.ofMinutes(10).plusSeconds(1));

        assertThat(oauthLoginAttemptService.consume("state-value")).isNull();
    }

    @Test
    @DisplayName("PKCE 미사용 제공자는 code_verifier 없이 저장")
    void allowsNullCodeVerifier() {
        AuthSession session = givenSession();
        oauthLoginAttemptService.start(session, "state-value", null, REDIRECT_URI);
        em.flush();

        assertThat(oauthLoginAttemptService.consume("state-value").getCodeVerifier()).isNull();
    }

    /**
     * 테스트 트랜잭션을 끄는 이유: 커밋되지 않은 데이터는 다른 스레드가 볼 수 없음.
     * 단일 UPDATE가 아니라 조회 후 저장 방식이면 여러 스레드가 함께 통과.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시에 소비해도 한 번만 성공한다")
    void consumesOnceUnderConcurrency() throws Exception {
        String state = "concurrent-state";
        oauthLoginAttemptService.start(givenSession(), state, "verifier", REDIRECT_URI);

        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger consumed = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    ready.countDown();
                    try {
                        fire.await();
                        if (oauthLoginAttemptService.consume(state) != null) {
                            consumed.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (RuntimeException e) {
                        errors.add(e);
                    }
                });
            }
            ready.await();
            fire.countDown();
        }

        assertThat(errors).isEmpty();
        assertThat(consumed.get()).isEqualTo(1);
    }

    private AuthSession givenSession() {
        return authSessionRepository.save(AuthSession.anonymous(
                SessionTokens.hash(SessionTokens.newToken()), SessionTokens.newToken(),
                Instant.now(CLOCK), Duration.ofHours(2), Duration.ofHours(24)));
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
