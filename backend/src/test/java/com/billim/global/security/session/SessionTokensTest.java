package com.billim.global.security.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 쿠키 원문 미저장 전제를 지탱하는 토큰 생성·해시 규칙 */
class SessionTokensTest {

    @Test
    @DisplayName("토큰은 매번 다르다")
    void generatesDistinctTokens() {
        assertThat(SessionTokens.newToken()).isNotEqualTo(SessionTokens.newToken());
    }

    @Test
    @DisplayName("토큰은 URL 안전 문자만 사용")
    void usesUrlSafeCharacters() {
        assertThat(SessionTokens.newToken()).matches("[A-Za-z0-9_-]+");
    }

    @Test
    @DisplayName("해시는 char(64) 컬럼 길이와 일치")
    void hashFitsColumnLength() {
        assertThat(SessionTokens.hash(SessionTokens.newToken()))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("같은 토큰은 같은 해시 — 해시로 조회가 성립")
    void hashIsDeterministic() {
        String token = SessionTokens.newToken();
        assertThat(SessionTokens.hash(token)).isEqualTo(SessionTokens.hash(token));
    }

    @Test
    @DisplayName("해시에서 원문을 알 수 없다")
    void hashDiffersFromToken() {
        String token = SessionTokens.newToken();
        assertThat(SessionTokens.hash(token)).isNotEqualTo(token);
    }
}
