package com.billim.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Netlify 서명(x-nf-sign) 판정 확인.
 * 위조·만료 토큰을 통과시키면 IP 헤더 위조로 요청 제한 우회, 정상 토큰을 거부하면 사용자 전원이 프록시 IP로 묶여 차단.
 */
class ProxySignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private static final String HS256_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ProxySignatureVerifier verifier =
            new ProxySignatureVerifier(JsonMapper.builder().build(), clock, SECRET);

    @Test
    @DisplayName("같은 비밀값으로 서명된 유효 토큰은 신뢰한다")
    void trustsValidToken() throws Exception {
        assertThat(verifier.trusts(sign(HS256_HEADER, claims("netlify", NOW.plusSeconds(60)), SECRET))).isTrue();
    }

    @Test
    @DisplayName("다른 비밀값으로 서명된 토큰은 거부한다")
    void rejectsWrongSecret() throws Exception {
        assertThat(verifier.trusts(sign(HS256_HEADER, claims("netlify", NOW.plusSeconds(60)), "other"))).isFalse();
    }

    @Test
    @DisplayName("만료된 토큰은 거부한다")
    void rejectsExpiredToken() throws Exception {
        assertThat(verifier.trusts(sign(HS256_HEADER, claims("netlify", NOW.minusSeconds(60)), SECRET))).isFalse();
    }

    @Test
    @DisplayName("발급자가 netlify가 아니면 거부한다")
    void rejectsWrongIssuer() throws Exception {
        assertThat(verifier.trusts(sign(HS256_HEADER, claims("other", NOW.plusSeconds(60)), SECRET))).isFalse();
    }

    @Test
    @DisplayName("HS256이 아닌 알고리즘 표기는 거부한다")
    void rejectsOtherAlgorithm() throws Exception {
        assertThat(verifier.trusts(sign("{\"alg\":\"none\"}", claims("netlify", NOW.plusSeconds(60)), SECRET)))
                .isFalse();
    }

    @Test
    @DisplayName("없거나 형식이 깨진 토큰은 거부한다")
    void rejectsMissingOrMalformedToken() {
        assertThat(verifier.trusts(null)).isFalse();
        assertThat(verifier.trusts("not-a-token")).isFalse();
        assertThat(verifier.trusts("a.b.c")).isFalse();
        assertThat(verifier.trusts("%%%.%%%.%%%")).isFalse();
    }

    @Test
    @DisplayName("비밀값이 비면 검증을 생략하고 신뢰한다")
    void trustsAllWhenDisabled() {
        ProxySignatureVerifier disabled = new ProxySignatureVerifier(JsonMapper.builder().build(), clock, "");

        assertThat(disabled.trusts(null)).isTrue();
    }

    private static String claims(String issuer, Instant exp) {
        return "{\"deploy_context\":\"production\",\"exp\":%d,\"iss\":\"%s\",\"site_url\":\"https://billim.netlify.app\"}"
                .formatted(exp.getEpochSecond(), issuer);
    }

    private static String sign(String header, String payload, String secret) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String signingInput = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return signingInput + "." + encoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }
}
