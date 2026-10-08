package com.billim.global.config;

import com.billim.global.exception.ErrorCode;
import com.billim.global.security.KakaoOAuth2UserService;
import com.billim.global.security.SecurityErrorResponseWriter;
import com.billim.global.security.oauth.DbAuthorizationRequestRepository;
import com.billim.global.security.oauth.LoginFailureHandler;
import com.billim.global.security.session.AuthSessionCsrfTokenRepository;
import com.billim.global.security.session.AuthSessionSecurityContextRepository;
import com.billim.global.security.session.LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
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
    private final AuthSessionSecurityContextRepository securityContextRepository;
    private final AuthSessionCsrfTokenRepository csrfTokenRepository;
    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final DbAuthorizationRequestRepository authorizationRequestRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
                // 인증 복원을 HTTP 세션이 아닌 auth_sessions에서 수행
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // CSRF 토큰도 같은 세션에 보관
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated())
                // HTTP 세션 미사용. 인증·CSRF·OAuth state를 모두 DB에 보관
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2Login(login -> login
                        // 로그인 시작은 AuthController가 담당. 여기서는 콜백이 쓸 저장소만 지정
                        // baseUri를 /api/v1/auth로 두면 /auth/csrf·/auth/logout까지 등록 ID로 해석됨
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(authorizationRequestRepository))
                        .redirectionEndpoint(endpoint -> endpoint.baseUri("/api/v1/auth/kakao/callback"))
                        .userInfoEndpoint(userInfo -> userInfo.userService(kakaoOAuth2UserService))
                        // defaultSuccessUrl 대신 핸들러에서 세션 회전 후 이동
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler))
                // 로그아웃은 AuthController가 세션 폐기·쿠키 만료를 직접 수행
                .logout(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                errorResponseWriter.write(response, ErrorCode.UNAUTHENTICATED))
                        // CSRF 실패와 권한 부족 구분. 프론트엔드는 CSRF_INVALID일 때만 토큰 폐기 후 재시도
                        .accessDeniedHandler((request, response, exception) ->
                                errorResponseWriter.write(response, exception instanceof CsrfException
                                        ? ErrorCode.CSRF_INVALID
                                        : ErrorCode.FORBIDDEN)))
                .build();
    }
}
