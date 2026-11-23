package com.billim.global.config;

import com.billim.global.security.session.SessionCookies;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서. 원본은 {@code docs/api/API 명세서.md}이고, 여기서는 연동 확인용 Swagger UI만 구성한다.
 *
 * <p>인증은 쿠키 세션 + CSRF 토큰이며 둘 다 필요한 AND 조건이다 (명세 0.2).
 * 변경 요청(POST/PUT/PATCH/DELETE)은 {@code X-CSRF-TOKEN} 없이 보내면 403이다.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    private static final String SESSION_COOKIE = "sessionCookie";
    private static final String CSRF_TOKEN = "csrfToken";

    @Bean
    public OpenAPI billimOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BILLIM API")
                        .version("v1")
                        .description("""
                                이웃 간 생활용품 무료 대여 서비스 BILLIM의 API.
                                ID는 JSON 문자열, 일시는 UTC RFC 3339, 날짜는 KST YYYY-MM-DD다.
                                실패 응답은 모두 ApiError 형식이며 requestId는 응답 헤더 X-Request-Id와 같다."""))
                .addServersItem(new Server().url("/").description("현재 호스트"))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(SessionCookies.NAME)
                                .description("카카오 로그인 후 발급되는 세션 쿠키"))
                        .addSecuritySchemes(CSRF_TOKEN, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-CSRF-TOKEN")
                                .description("GET /api/v1/auth/csrf로 받은 토큰. 변경 요청에 필수")))
                // 두 요구사항을 한 항목에 담아 AND 조건으로 표시
                .addSecurityItem(new SecurityRequirement()
                        .addList(SESSION_COOKIE)
                        .addList(CSRF_TOKEN));
    }
}
