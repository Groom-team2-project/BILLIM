package com.billim.global.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;

/**
 * Netlify 서명 프록시(x-nf-sign) 검증. 서명이 맞는 요청만 프록시가 실은 클라이언트 IP 헤더를 신뢰.
 * EC2 8080이 외부에 열려 있어 직접 호출로 IP 헤더를 위조할 수 있기 때문.
 * 토큰은 HS256 JWS — 비밀값은 netlify.toml의 signed가 가리키는 사이트 환경 변수와 같은 값.
 *
 * <p>비밀값이 비면 모든 헤더 불신(접속 주소로 계수) — 설정 누락이 곧 우회 경로가 되지 않도록.
 * 운영에서 비밀값이 빠지면 사용자들이 Netlify IP로 묶여 허용량을 나눠 씀 — 기동 경고로 확인.
 */
public class ProxySignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(ProxySignatureVerifier.class);

    private static final String ALGORITHM = "HmacSHA256";
    private static final String ISSUER = "netlify";
    /** Netlify와 EC2 간 시계 오차 허용(초) */
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SecretKeySpec key;

    public ProxySignatureVerifier(ObjectMapper objectMapper, Clock clock, String secret) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.key = secret == null || secret.isBlank()
                ? null
                : new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        if (key == null) {
            log.warn("프록시 서명 비밀값 없음 — 클라이언트 IP 헤더 무시, 접속 주소로 계수");
        }
    }

    public boolean trusts(String token) {
        if (key == null || token == null) {
            return false;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }
        try {
            if (!validSignature(parts[0] + "." + parts[1], parts[2])) {
                return false;
            }
            JsonNode header = decode(parts[0]);
            JsonNode claims = decode(parts[1]);
            // alg 확인 — 서명 통과 후라도 none 등 다른 알고리즘 표기는 거부
            return "HS256".equals(header.path("alg").asString(""))
                    && ISSUER.equals(claims.path("iss").asString(""))
                    && claims.path("exp").canConvertToLong()
                    && claims.path("exp").asLong() + CLOCK_SKEW_SECONDS > clock.instant().getEpochSecond();
        } catch (IllegalArgumentException | GeneralSecurityException | JacksonException e) {
            // 형식이 깨진 토큰 — 위조로 보고 불신
            return false;
        }
    }

    private boolean validSignature(String signingInput, String signature) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(key);
        byte[] expected = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        // 상수 시간 비교 — 응답 시간으로 서명 추측 방지
        return MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(signature));
    }

    private JsonNode decode(String part) {
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8));
    }
}
