package com.billim.global.config;

import com.billim.global.exception.ErrorCode;
import com.billim.global.security.KakaoOAuth2UserService;
import com.billim.global.security.SecurityErrorResponseWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 로그인 없이 열어두는 경로. 상태 점검과 API 문서 */
    private static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
    };

    private final KakaoOAuth2UserService kakaoOAuth2UserService;
    private final SecurityErrorResponseWriter errorResponseWriter;
    private final String loginSuccessUrl;

    public SecurityConfig(KakaoOAuth2UserService kakaoOAuth2UserService,
                          SecurityErrorResponseWriter errorResponseWriter,
                          @Value("${billim.auth.login-success-url}") String loginSuccessUrl) {
        this.kakaoOAuth2UserService = kakaoOAuth2UserService;
        this.errorResponseWriter = errorResponseWriter;
        this.loginSuccessUrl = loginSuccessUrl;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .userInfoEndpoint(userInfo -> userInfo.userService(kakaoOAuth2UserService))
                        .defaultSuccessUrl(loginSuccessUrl, true))

                .logout(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponseWriter.write(response, ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponseWriter.write(response, ErrorCode.FORBIDDEN)))
                .build();
    }
}
