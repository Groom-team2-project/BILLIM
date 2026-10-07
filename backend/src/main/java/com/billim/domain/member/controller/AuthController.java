package com.billim.domain.member.controller;

import com.billim.domain.member.dto.CsrfTokenResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * 익명도 사용.
     * CsrfToken 파라미터는 Spring Security가 주입하며, 값을 읽는 시점에 토큰이 생성되고 세션에 저장됨.
     * BILLIM_SESSION 쿠키 발급은 세션 저장소 교체 작업에서 처리.
     */
    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(csrfToken.getToken()));
    }
}
