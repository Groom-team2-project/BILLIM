package com.billim.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

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
}
