package com.billim.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;

/**
 * 로그인 시작을 컨트롤러에서 직접 처리하므로 리졸버를 빈으로 노출.
 * state·PKCE 생성 규칙은 Spring 기본 구현을 그대로 사용.
 */
@Configuration
public class OAuth2RequestResolverConfig {

    /** 기본 필터 경로. 컨트롤러가 registrationId로 직접 호출하므로 매칭에는 쓰이지 않음 */
    private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";

    @Bean
    public OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, AUTHORIZATION_BASE_URI);
    }
}
