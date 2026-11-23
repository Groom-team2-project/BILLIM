package com.billim.global.security.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/** BILLIM_SESSION 쿠키 입출력. 속성은 API 명세 0.2 */
@Component
public class SessionCookies {

    public static final String NAME = "BILLIM_SESSION";

    private final boolean secure;
    private final Duration maxAge;

    public SessionCookies(@Value("${billim.auth.cookie-secure}") boolean secure,
                          @Value("${billim.auth.session-absolute-ttl}") Duration maxAge) {
        this.secure = secure;
        this.maxAge = maxAge;
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, maxAge).toString());
    }

    /** 로그아웃·회전 시 즉시 삭제. maxAge 0이 브라우저 삭제 지시 */
    public void expire(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                // JavaScript 접근 차단. XSS로도 세션 탈취 불가
                .httpOnly(true)
                // 운영(HTTPS)만 true. 로컬 http에서 true면 쿠키가 아예 전송되지 않음
                .secure(secure)
                // 최상위 이동(OAuth 콜백)에는 실리고, 타 사이트 요청에는 실리지 않음
                .sameSite("Lax")
                .path("/")
                .maxAge(age)
                .build();
    }
}
