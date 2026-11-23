package com.billim.global.security.session;

import com.billim.domain.member.service.AuthSessionService;
import com.billim.global.security.LoginMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 카카오 인증 성공 후 BILLIM_SESSION 발급과 프론트엔드 복귀 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthSessionService authSessionService;
    private final SessionCookies sessionCookies;
    private final CurrentAuthSession currentAuthSession;
    private final String loginSuccessUrl;

    public LoginSuccessHandler(AuthSessionService authSessionService,
                               SessionCookies sessionCookies,
                               CurrentAuthSession currentAuthSession,
                               @Value("${billim.auth.login-success-url}") String loginSuccessUrl) {
        this.authSessionService = authSessionService;
        this.sessionCookies = sessionCookies;
        this.currentAuthSession = currentAuthSession;
        this.loginSuccessUrl = loginSuccessUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof LoginMember principal)) {
            throw new IllegalStateException("인증 주체가 LoginMember가 아님: " + authentication.getPrincipal());
        }
        // 로그인 전 세션은 폐기 대상으로 전달 (세션 회전)
        String previousToken = sessionCookies.read(request).orElse(null);
        AuthSessionService.Issued issued = authSessionService.rotateForMember(previousToken, principal.memberId());

        sessionCookies.write(response, issued.token());
        currentAuthSession.set(request, issued.session());
        response.sendRedirect(loginSuccessUrl);
    }
}
