package com.billim.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * IP별 고정 윈도우 판정 확인.
 * 키 분리(경로 그룹·IP)나 윈도우 초기화가 틀리면 정상 사용자 차단 또는 남용 통과.
 */
class AuthRateLimitFilterTest {

    private static final String PROXY_HEADER = "x-nf-client-connection-ip";
    private static final int CSRF_LIMIT = 3;
    private static final int LOGIN_LIMIT = 2;
    private static final int MAX_TRACKED = 4;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-27T00:00:00Z"));
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new SecurityErrorResponseWriter(JsonMapper.builder().build()),
            clock, PROXY_HEADER, new ProxySignatureVerifier(JsonMapper.builder().build(), clock, ""),
            CSRF_LIMIT, LOGIN_LIMIT, MAX_TRACKED);

    @Test
    @DisplayName("한도 내 요청은 통과한다")
    void allowsWithinLimit() throws Exception {
        for (int i = 0; i < CSRF_LIMIT; i++) {
            assertThat(perform("/api/v1/auth/csrf", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("한도를 넘으면 429 RATE_LIMITED와 Retry-After를 돌려준다")
    void rejectsOverLimit() throws Exception {
        for (int i = 0; i < CSRF_LIMIT; i++) {
            perform("/api/v1/auth/csrf", "1.1.1.1");
        }

        MockHttpServletResponse response = perform("/api/v1/auth/csrf", "1.1.1.1");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    }

    @Test
    @DisplayName("차단된 요청은 다음 필터로 넘어가지 않는다")
    void blockedRequestDoesNotReachChain() throws Exception {
        for (int i = 0; i < CSRF_LIMIT; i++) {
            perform("/api/v1/auth/csrf", "1.1.1.1");
        }
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/v1/auth/csrf", "1.1.1.1"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("분이 지나면 허용량이 초기화된다")
    void resetsAfterWindowPasses() throws Exception {
        for (int i = 0; i < CSRF_LIMIT; i++) {
            perform("/api/v1/auth/csrf", "1.1.1.1");
        }
        clock.advance(Duration.ofMinutes(1));

        assertThat(perform("/api/v1/auth/csrf", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("IP가 다르면 허용량도 별개다")
    void countsPerClientIp() throws Exception {
        for (int i = 0; i < CSRF_LIMIT; i++) {
            perform("/api/v1/auth/csrf", "1.1.1.1");
        }

        assertThat(perform("/api/v1/auth/csrf", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("csrf를 소진해도 로그인 시작 허용량은 남는다")
    void csrfAndLoginGroupsAreIndependent() throws Exception {
        for (int i = 0; i <= CSRF_LIMIT; i++) {
            perform("/api/v1/auth/csrf", "1.1.1.1");
        }

        assertThat(perform("/api/v1/auth/kakao", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    /** 제공자를 번갈아 부르면 허용량이 두 배가 되면 안 됨 */
    @Test
    @DisplayName("카카오와 네이버는 로그인 허용량 하나를 나눠 쓴다")
    void kakaoAndNaverShareLoginBudget() throws Exception {
        perform("/api/v1/auth/kakao", "1.1.1.1");
        perform("/api/v1/auth/naver", "1.1.1.1");

        assertThat(perform("/api/v1/auth/kakao", "1.1.1.1").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    /** 자격증명 무차별 대입 방어 — 소셜 시작과 같은 허용량으로 계수 */
    @Test
    @DisplayName("관리자 로그인도 로그인 허용량을 함께 쓴다")
    void adminLoginSharesLoginBudget() throws Exception {
        perform("/api/v1/auth/admin/login", "1.1.1.1");
        perform("/api/v1/auth/kakao", "1.1.1.1");

        assertThat(perform("/api/v1/auth/admin/login", "1.1.1.1").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("대상 밖 경로는 세지 않는다")
    void ignoresOtherPaths() throws Exception {
        for (int i = 0; i < CSRF_LIMIT * 2; i++) {
            assertThat(perform("/api/v1/auth/logout", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("프록시 헤더가 없으면 접속 주소로 센다")
    void fallsBackToRemoteAddr() throws Exception {
        for (int i = 0; i < CSRF_LIMIT; i++) {
            MockHttpServletRequest request = request("/api/v1/auth/csrf", null);
            request.setRemoteAddr("9.9.9.9");
            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletRequest request = request("/api/v1/auth/csrf", null);
        request.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    /** 직접 접속(프록시 우회)의 과대 헤더 값으로 키를 무한정 만드는 것 방지 */
    @Test
    @DisplayName("IP 표기보다 긴 헤더 값은 접속 주소로 대체한다")
    void rejectsOversizedHeaderValue() throws Exception {
        for (int i = 0; i < CSRF_LIMIT; i++) {
            perform("/api/v1/auth/csrf", "가".repeat(46));
        }

        assertThat(perform("/api/v1/auth/csrf", "가".repeat(46) + "다른값").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("추적 상한이 차면 새 주소는 차단하고 기존 주소는 받는다")
    void blocksNewKeysWhenTrackingIsFull() throws Exception {
        for (int i = 1; i <= MAX_TRACKED; i++) {
            perform("/api/v1/auth/csrf", "10.0.0." + i);
        }

        assertThat(perform("/api/v1/auth/csrf", "10.0.1.1").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(perform("/api/v1/auth/csrf", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("추적 상한이 차도 지난 윈도우를 비우면 새 주소를 받는다")
    void sweepsStaleWindowsWhenFull() throws Exception {
        for (int i = 1; i <= MAX_TRACKED; i++) {
            perform("/api/v1/auth/csrf", "10.0.0." + i);
        }
        clock.advance(Duration.ofMinutes(1));

        assertThat(perform("/api/v1/auth/csrf", "10.0.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("서명 검증이 켜지면 서명 없는 IP 헤더는 무시하고 접속 주소로 계수한다")
    void ignoresUnsignedProxyHeaderWhenVerificationEnabled() throws Exception {
        AuthRateLimitFilter signedFilter = new AuthRateLimitFilter(
                new SecurityErrorResponseWriter(JsonMapper.builder().build()), clock, PROXY_HEADER,
                new ProxySignatureVerifier(JsonMapper.builder().build(), clock, "secret"),
                CSRF_LIMIT, LOGIN_LIMIT, MAX_TRACKED);
        for (int i = 0; i < CSRF_LIMIT; i++) {
            signedFilter.doFilter(request("/api/v1/auth/csrf", "10.0.0." + i),
                    new MockHttpServletResponse(), new MockFilterChain());
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        signedFilter.doFilter(request("/api/v1/auth/csrf", "10.0.0.99"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("한도가 0 이하인 설정은 기동을 거부한다")
    void rejectsNonPositiveLimits() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AuthRateLimitFilter(
                new SecurityErrorResponseWriter(JsonMapper.builder().build()),
                clock, PROXY_HEADER, new ProxySignatureVerifier(JsonMapper.builder().build(), clock, ""),
                0, LOGIN_LIMIT, MAX_TRACKED));
    }

    private MockHttpServletResponse perform(String path, String clientIp) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(path, clientIp), response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String path, String clientIp) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (clientIp != null) {
            request.addHeader(PROXY_HEADER, clientIp);
        }
        return request;
    }

    /** 경과 시간을 직접 조작하는 테스트용 Clock */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
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
