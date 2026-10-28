package com.billim.global.config;

import com.billim.global.security.AuthRateLimitFilter;
import com.billim.global.security.ProxySignatureVerifier;
import com.billim.global.security.SecurityErrorResponseWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * 요청 제한 필터 등록.
 * 필터를 @Component로 두지 않는 이유: @WebMvcTest가 Filter 컴포넌트를 슬라이스에 포함해
 * 전역 빈(응답 작성기·Clock) 부재로 컨트롤러 테스트가 기동 실패. 설정 클래스는 슬라이스 밖.
 */
@Configuration
public class AuthRateLimitConfig {

    // 긴급 차단 해제용. 성능 측정처럼 한 주소에서 다량 호출하는 작업도 이 플래그로 끔
    @Bean
    @ConditionalOnProperty(name = "billim.auth.rate-limit-enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(
            SecurityErrorResponseWriter errorResponseWriter,
            Clock clock,
            ObjectMapper objectMapper,
            @Value("${billim.auth.rate-limit-client-ip-header}") String clientIpHeader,
            @Value("${billim.auth.rate-limit-proxy-signature-secret}") String proxySignatureSecret,
            @Value("${billim.auth.rate-limit-csrf-per-minute}") int csrfPerMinute,
            @Value("${billim.auth.rate-limit-login-per-minute}") int loginPerMinute,
            @Value("${billim.auth.rate-limit-max-tracked-keys}") int maxTrackedKeys) {
        var verifier = new ProxySignatureVerifier(objectMapper, clock, proxySignatureSecret);
        var registration = new FilterRegistrationBean<>(new AuthRateLimitFilter(
                errorResponseWriter, clock, clientIpHeader, verifier, csrfPerMinute, loginPerMinute, maxTrackedKeys));
        // 시큐리티 체인(-100) 앞 — 차단된 요청이 세션·state 행을 만들기 전에 종료
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
