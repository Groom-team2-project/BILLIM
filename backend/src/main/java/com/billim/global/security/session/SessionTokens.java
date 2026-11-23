package com.billim.global.security.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 세션 쿠키·CSRF·OAuth state용 토큰 생성과 해시 */
public final class SessionTokens {

    /** ERD: 256비트 이상 무작위 */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionTokens() {
    }

    /** 쿠키·state에 실어 보내는 원문. URL 안전 문자만 사용 */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 저장용 SHA-256 16진수 64자. 컬럼 타입 char(64)와 길이 일치.
     * 원문 미저장 원칙에 따라 조회도 해시로 수행. (ERD auth_sessions.token_hash)
     */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM 필수 구현. 도달 불가
            throw new IllegalStateException(e);
        }
    }
}
