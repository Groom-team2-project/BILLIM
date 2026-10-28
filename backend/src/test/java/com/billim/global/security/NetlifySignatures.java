package com.billim.global.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

/** 테스트용 Netlify 서명(x-nf-sign) 생성 — 운영 토큰과 같은 HS256 JWS 형식 */
final class NetlifySignatures {

    private NetlifySignatures() {
    }

    static String sign(String secret, Instant exp) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String claims = "{\"deploy_context\":\"production\",\"exp\":%d,\"iss\":\"netlify\"}"
                .formatted(exp.getEpochSecond());
        String signingInput = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return signingInput + "." + encoder.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
