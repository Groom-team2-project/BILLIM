package com.billim.global.config;

import com.billim.global.security.ProxySignatureFilter;
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
 * Netlify 서명 검증기와 서명 강제 필터 등록. 검증기는 요청 제한 필터와 공유.
 * 필터를 @Component로 두지 않는 이유는 AuthRateLimitConfig와 동일 — @WebMvcTest 슬라이스 기동 실패.
 */
@Configuration
public class ProxySignatureConfig {

    @Bean
    public ProxySignatureVerifier proxySignatureVerifier(
            ObjectMapper objectMapper, Clock clock,
            @Value("${billim.auth.rate-limit-proxy-signature-secret}") String secret) {
        return new ProxySignatureVerifier(objectMapper, clock, secret);
    }

    /**
     * 옵트인 — 배포는 상태 확인 없이 재시작만 하므로, 머지만으로 전 요청 거부가 일어나지 않도록 기본 꺼짐.
     * 켰는데 비밀값이 비면 기동 거부 — 의도한 설정이라 즉시 드러나는 편이 안전.
     */
    @Bean
    @ConditionalOnProperty(name = "billim.auth.require-proxy-signature", havingValue = "true")
    public FilterRegistrationBean<ProxySignatureFilter> proxySignatureFilter(
            ProxySignatureVerifier verifier, SecurityErrorResponseWriter errorResponseWriter) {
        if (!verifier.isConfigured()) {
            throw new IllegalStateException(
                    "billim.auth.require-proxy-signature=true에는 PROXY_SIGNATURE_SECRET 필요");
        }
        var registration = new FilterRegistrationBean<>(new ProxySignatureFilter(verifier, errorResponseWriter));
        // 요청 ID(최우선) 다음, 요청 제한 앞 — 서명 없는 요청은 계수 없이 종료
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
