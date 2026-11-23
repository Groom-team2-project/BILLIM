package com.billim.global.security;

import com.billim.global.config.AuthRateLimitConfig;
import com.billim.global.config.ProxySignatureConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /api 진입점을 Netlify 프록시로 제한하는 서명 강제 확인.
 * 통과 판정이 틀리면 직접 호출로 요청 제한 우회, 거부 판정이 틀리면 정상 사용자 전원 차단.
 */
class ProxySignatureFilterTest {

    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final ProxySignatureFilter filter = new ProxySignatureFilter(
            new ProxySignatureVerifier(objectMapper, clock, SECRET), new SecurityErrorResponseWriter(objectMapper));

    @Test
    @DisplayName("서명된 API 요청은 통과한다")
    void passesSignedApiRequest() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = perform("/api/v1/members/me",
                NetlifySignatures.sign(SECRET, NOW.plusSeconds(60)), chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("서명 없는 API 요청은 403이고 다음 필터로 넘어가지 않는다")
    void rejectsUnsignedApiRequest() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = perform("/api/v1/auth/csrf", null, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("다른 비밀값·만료된 서명은 403")
    void rejectsInvalidSignatures() throws Exception {
        assertThat(perform("/api/v1/auth/csrf", NetlifySignatures.sign("other-secret", NOW.plusSeconds(60)),
                new MockFilterChain()).getStatus()).isEqualTo(403);
        assertThat(perform("/api/v1/auth/csrf", NetlifySignatures.sign(SECRET, NOW.minusSeconds(3600)),
                new MockFilterChain()).getStatus()).isEqualTo(403);
    }

    /** 채팅은 별도 CloudFront로 들어오며 일회용 연결권으로 인증 — 서명 대상 아님 */
    @Test
    @DisplayName("API 밖 경로는 서명 없이 통과한다")
    void ignoresNonApiPaths() throws Exception {
        for (String path : new String[]{"/ws/chat", "/actuator/health", "/apix"}) {
            MockFilterChain chain = new MockFilterChain();
            perform(path, null, chain);
            assertThat(chain.getRequest()).as(path).isNotNull();
        }
    }

    @Test
    @DisplayName("기본은 꺼져 있어 필터를 등록하지 않는다")
    void disabledByDefault() {
        contextRunner().withPropertyValues("billim.auth.rate-limit-proxy-signature-secret=" + SECRET)
                .run(context -> assertThat(context).doesNotHaveBean("proxySignatureFilter"));
    }

    /** 배포는 상태 확인 없이 재시작 — 비밀값 누락을 조용히 넘기면 전 요청 403 */
    @Test
    @DisplayName("켰는데 비밀값이 없으면 기동을 거부한다")
    void failsStartupWithoutSecret() {
        contextRunner().withPropertyValues("billim.auth.require-proxy-signature=true",
                        "billim.auth.rate-limit-proxy-signature-secret=")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("PROXY_SIGNATURE_SECRET"));
    }

    @Test
    @DisplayName("켜면 요청 제한보다 먼저 실행된다")
    void runsBeforeRateLimit() {
        contextRunner().withPropertyValues("billim.auth.require-proxy-signature=true",
                        "billim.auth.rate-limit-proxy-signature-secret=" + SECRET)
                .run(context -> {
                    int signature = context.getBean("proxySignatureFilter", FilterRegistrationBean.class).getOrder();
                    int rateLimit = context.getBean("authRateLimitFilter", FilterRegistrationBean.class).getOrder();
                    assertThat(signature).isLessThan(rateLimit);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProxySignatureConfig.class, AuthRateLimitConfig.class)
                .withBean(ObjectMapper.class, () -> objectMapper)
                .withBean(Clock.class, () -> clock)
                .withBean(SecurityErrorResponseWriter.class, () -> new SecurityErrorResponseWriter(objectMapper))
                .withPropertyValues(
                        "billim.auth.rate-limit-client-ip-header=x-nf-client-connection-ip",
                        "billim.auth.rate-limit-csrf-per-minute=30",
                        "billim.auth.rate-limit-login-per-minute=10",
                        "billim.auth.rate-limit-max-tracked-keys=100");
    }

    private MockHttpServletResponse perform(String path, String signature, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (signature != null) {
            request.addHeader("x-nf-sign", signature);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
