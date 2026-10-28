package com.billim.domain.member.controller;

import com.billim.domain.member.dto.AdminLoginRequest;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.service.AdminLoginService;
import com.billim.domain.member.service.AuthSessionService;
import com.billim.global.security.session.CurrentAuthSession;
import com.billim.global.security.session.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 로그인. /api/v1/auth 아래에 두어 공개 경로(permitAll)와 요청 제한 대상에 포함.
 * CSRF는 다른 변경 요청과 동일 — 익명 세션의 토큰(/auth/csrf)을 싣고 호출.
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminLoginController {

    private final AdminLoginService adminLoginService;
    private final AuthSessionService authSessionService;
    private final SessionCookies sessionCookies;
    private final CurrentAuthSession currentAuthSession;

    /**
     * 자격증명 대조 후 소셜 로그인 성공과 같은 세션 회전·쿠키 발급(LoginSuccessHandler와 동일 경로).
     * 응답 본문 없음 — 프론트엔드는 소셜 복귀와 같게 /members/me로 상태 구성.
     */
    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody AdminLoginRequest loginRequest,
                                      HttpServletRequest request, HttpServletResponse response) {
        Member member = adminLoginService.login(loginRequest.loginId(), loginRequest.password());

        // 로그인 전 세션은 폐기 대상으로 전달 (세션 회전)
        String previousToken = sessionCookies.read(request).orElse(null);
        AuthSessionService.Issued issued = authSessionService.rotateForMember(previousToken, member.getId());

        sessionCookies.write(response, issued.token());
        currentAuthSession.set(request, issued.session());
        return ResponseEntity.noContent().build();
    }
}
