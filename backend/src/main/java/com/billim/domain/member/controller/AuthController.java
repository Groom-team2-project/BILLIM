package com.billim.domain.member.controller;

import com.billim.domain.member.dto.CsrfTokenResponse;
import com.billim.domain.member.service.AuthSessionService;
import com.billim.global.security.session.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthSessionService authSessionService;
    private final SessionCookies sessionCookies;

    /**
     * 익명도 사용.
     * 세션이 없으면 AuthSessionCsrfTokenRepository가 발급하고 BILLIM_SESSION 쿠키를 내려보냄.
     * 응답 값은 요청마다 달라지는 마스킹 토큰이며 원본은 auth_sessions에 남음.
     */
    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(csrfToken.getToken()));
    }

    /** 세션 폐기와 쿠키 만료. 현재 세션만 대상 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionCookies.read(request).ifPresent(authSessionService::revoke);
        sessionCookies.expire(response);
        return ResponseEntity.noContent().build();
    }
}
