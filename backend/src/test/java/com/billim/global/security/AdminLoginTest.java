package com.billim.global.security;

import com.billim.MockChatRedisListener;
import com.billim.global.security.session.SessionCookies;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 전용 로그인의 끝단 확인 — 자격증명으로 발급된 세션이 실제 관리자 경로 보안을 통과해야 함.
 * 세부 판정(틀린 자격증명·비활성)은 AdminLoginServiceTest 담당.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.auth.admin-login-id=billim-admin",
        // bcrypt("test-admin-pw"). 강도 4 — 테스트 속도
        "billim.auth.admin-password-hash=$2y$04$lAfLhNzSpfrBolppuYsq1.Rz0gsTmlQySKyKTZfp1LFd34KU5FCq."
})
@AutoConfigureMockMvc
@Import(AdminLoginTest.MySql.class)
@MockChatRedisListener
class AdminLoginTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("올바른 자격증명은 로그인 세션 쿠키를 발급한다")
    void issuesSessionOnValidCredentials() throws Exception {
        String setCookie = mockMvc.perform(login("billim-admin", "test-admin-pw"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains(SessionCookies.NAME + "=").contains("HttpOnly");
    }

    @Test
    @DisplayName("발급된 세션은 관리자 경로의 보안을 통과한다")
    void issuedSessionPassesAdminPathSecurity() throws Exception {
        Cookie adminCookie = loginAndReadCookie();

        mockMvc.perform(get("/api/v1/admin/ping").cookie(adminCookie))
                .andExpect(status().isNotFound());   // 핸들러 없는 경로라 보안 통과 시 404
    }

    @Test
    @DisplayName("틀린 비밀번호는 401이고 로그인 세션을 발급하지 않는다")
    void rejectsWrongPassword() throws Exception {
        String setCookie = mockMvc.perform(login("billim-admin", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNull();
    }

    /** 로그인 ID가 아닌 고정 식별자(LOCAL/admin)로 조회 — 반복 로그인이 회원을 늘리면 안 됨 */
    @Test
    @DisplayName("반복 로그인해도 관리자 회원은 하나다")
    void reusesSameAdminMemberAcrossLogins() throws Exception {
        mockMvc.perform(login("billim-admin", "test-admin-pw")).andExpect(status().isNoContent());
        mockMvc.perform(login("billim-admin", "test-admin-pw")).andExpect(status().isNoContent());

        Long admins = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM social_accounts WHERE provider = 'LOCAL'", Long.class);
        assertThat(admins).isEqualTo(1);
    }

    /** 자격증명이 맞아도 CSRF 없는 요청은 거부 — 로그인 CSRF로 피해자를 관리자 세션에 앉히는 공격 방지 */
    @Test
    @DisplayName("CSRF 토큰 없는 로그인 요청은 403 CSRF_INVALID")
    void rejectsLoginWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("billim-admin", "test-admin-pw")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private MockHttpServletRequestBuilder login(String loginId, String password) throws Exception {
        IssuedSession session = issueSession();
        return post("/api/v1/auth/admin/login")
                .cookie(session.cookie())
                .header("X-CSRF-TOKEN", session.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(loginId, password));
    }

    private static String body(String loginId, String password) {
        return "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password);
    }

    private Cookie loginAndReadCookie() throws Exception {
        String setCookie = mockMvc.perform(login("billim-admin", "test-admin-pw"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        return new Cookie(SessionCookies.NAME, cookieValue(setCookie));
    }

    /** 실제 발급 흐름으로 세션과 토큰 획득 (AuthSecurityTest와 같은 이유) */
    private IssuedSession issueSession() throws Exception {
        var response = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        return new IssuedSession(
                new Cookie(SessionCookies.NAME, cookieValue(response.getHeader(HttpHeaders.SET_COOKIE))),
                JsonPath.read(response.getContentAsString(), "$.csrfToken"));
    }

    private static String cookieValue(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private record IssuedSession(Cookie cookie, String csrfToken) {
    }
}
