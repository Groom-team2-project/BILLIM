package com.billim.global.security;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig의 공개 경로·인증 실패 응답과 CSRF 토큰 발급을 확인.
 * 인증 실패가 로그인 리다이렉트가 아니라 JSON이어야 프론트엔드가 401로 판정할 수 있음.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
@AutoConfigureMockMvc
@Import(AuthSecurityTest.MySql.class)
class AuthSecurityTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));   // compose.yaml과 같은 버전
        }
    }

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("CSRF 토큰은 비로그인도 발급받는다")
    void issuesCsrfTokenToAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
    }

    @Test
    @DisplayName("CSRF 토큰 응답은 캐시하지 않는다")
    void csrfResponseIsNotCached() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("비로그인 보호 경로는 리다이렉트가 아니라 401 JSON")
    void protectedPathReturnsJsonUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("상태 점검 경로는 로그인 없이 열려 있다")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /** 권한 부족(FORBIDDEN)과 구분 필요. 프론트엔드는 CSRF_INVALID일 때만 토큰 폐기 후 재시도 */
    @Test
    @DisplayName("CSRF 토큰 없는 변경 요청은 403 CSRF_INVALID")
    void postWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/items"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @DisplayName("CSRF 토큰 요청이 세션 쿠키를 발급한다")
    void issuesSessionCookie() throws Exception {
        String setCookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .contains(SessionCookies.NAME + "=")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .contains("Path=/");
    }

    @Test
    @DisplayName("쿠키를 다시 보내면 세션을 재발급하지 않는다")
    void reusesExistingSession() throws Exception {
        IssuedSession session = issueSession();

        String setCookie = mockMvc.perform(get("/api/v1/auth/csrf").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).isNull();
    }

    /** 세션 쿠키 존재와 인증 상태는 별개 */
    @Test
    @DisplayName("로그인 전 세션으로는 보호 경로에 접근할 수 없다")
    void anonymousSessionIsNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/items").cookie(issueSession().cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("발급받은 CSRF 토큰으로 변경 요청이 통과한다")
    void acceptsIssuedCsrfToken() throws Exception {
        IssuedSession session = issueSession();

        mockMvc.perform(logout(session))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("로그아웃은 세션을 폐기하고 쿠키를 만료시킨다")
    void logoutExpiresCookie() throws Exception {
        IssuedSession session = issueSession();

        String setCookie = mockMvc.perform(logout(session))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains(SessionCookies.NAME + "=").contains("Max-Age=0");
    }

    /** 폐기된 세션은 복원 불가. 새 세션 발급으로 이어짐 */
    @Test
    @DisplayName("로그아웃한 쿠키는 더 이상 세션으로 쓰이지 않는다")
    void revokedCookieIsRejected() throws Exception {
        IssuedSession session = issueSession();
        mockMvc.perform(logout(session)).andExpect(status().isNoContent());

        String setCookie = mockMvc.perform(get("/api/v1/auth/csrf").cookie(session.cookie()))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains(SessionCookies.NAME + "=");
    }

    @Test
    @DisplayName("로그인 시작은 카카오로 302 이동하며 세션을 발급한다")
    void startsKakaoLogin() throws Exception {
        var response = mockMvc.perform(get("/api/v1/auth/kakao"))
                .andExpect(status().isFound())
                .andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.LOCATION))
                .startsWith("https://kauth.kakao.com/oauth/authorize")
                .contains("state=")
                .contains("code_challenge=");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).contains(SessionCookies.NAME + "=");
    }

    /** state만 맞고 세션이 다르면 공격자가 피해자를 자기 계정으로 로그인시킬 수 있음 */
    @Test
    @DisplayName("세션 쿠키 없는 콜백은 거부한다")
    void rejectsCallbackFromAnotherBrowser() throws Exception {
        String state = startLoginAndReadState();

        mockMvc.perform(get("/api/v1/auth/kakao/callback").param("code", "dummy").param("state", state))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("저장되지 않은 state의 콜백은 거부한다")
    void rejectsUnknownState() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/callback").param("code", "dummy").param("state", "없는-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** 취소는 정상 행동이므로 오류 화면이 아니라 로그인 화면으로 복귀 */
    @Test
    @DisplayName("카카오에서 취소하면 로그인 화면으로 돌려보낸다")
    void redirectsToLoginOnUserCancel() throws Exception {
        IssuedSession session = issueSession();
        String state = startLoginAndReadState(session.cookie());

        mockMvc.perform(get("/api/v1/auth/kakao/callback")
                        .param("error", "access_denied")
                        .param("state", state)
                        .cookie(session.cookie()))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("error=OAUTH_CANCELED")));
    }

    /** HTTP 세션을 쓰면 다중 인스턴스에서 콜백이 실패 */
    @Test
    @DisplayName("HTTP 세션 쿠키는 발급하지 않는다")
    void doesNotIssueHttpSessionCookie() throws Exception {
        var response = mockMvc.perform(get("/api/v1/auth/kakao")).andReturn().getResponse();

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .noneMatch(cookie -> cookie.contains("JSESSIONID"));
    }

    private String startLoginAndReadState() throws Exception {
        return readState(mockMvc.perform(get("/api/v1/auth/kakao")));
    }

    private String startLoginAndReadState(Cookie cookie) throws Exception {
        return readState(mockMvc.perform(get("/api/v1/auth/kakao").cookie(cookie)));
    }

    /** getQueryParams()는 인코딩된 값을 돌려주므로 디코딩 필요. 미디코딩 시 state 불일치로 오탐 */
    private String readState(ResultActions result) throws Exception {
        String location = result.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        String encoded = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private MockHttpServletRequestBuilder logout(IssuedSession session) {
        return post("/api/v1/auth/logout")
                .cookie(session.cookie())
                .header("X-CSRF-TOKEN", session.csrfToken());
    }

    /**
     * 실제 발급 흐름으로 세션과 토큰 획득.
     * SecurityMockMvcRequestPostProcessors.csrf()는 필터의 CsrfTokenRepository를 테스트용으로 교체 후
     * 복원하지 않아 이후 테스트에서 세션 미발급.
     */
    private IssuedSession issueSession() throws Exception {
        var response = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse();
        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        String value = setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
        return new IssuedSession(new Cookie(SessionCookies.NAME, value),
                JsonPath.read(response.getContentAsString(), "$.csrfToken"));
    }

    private record IssuedSession(Cookie cookie, String csrfToken) {
    }
}
