package com.billim.domain.member.controller;

import com.billim.domain.member.dto.CsrfTokenResponse;
import com.billim.domain.member.dto.LogoutOthersResponse;
import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.service.AuthSessionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import com.billim.global.security.oauth.DbAuthorizationRequestRepository;
import com.billim.global.security.session.CurrentAuthSession;
import com.billim.global.security.session.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthSessionService authSessionService;
    private final SessionCookies sessionCookies;
    private final CurrentAuthSession currentAuthSession;
    private final OAuth2AuthorizationRequestResolver authorizationRequestResolver;
    private final DbAuthorizationRequestRepository authorizationRequestRepository;

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

    @GetMapping("/kakao")
    public void startKakaoLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        startLogin("kakao", request, response);
    }

    @GetMapping("/naver")
    public void startNaverLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        startLogin("naver", request, response);
    }

    /**
     * 브라우저 최상위 이동. state·PKCE 생성은 Spring 리졸버에 위임하고 보관만 직접 수행.
     * Spring 기본 필터(baseUri 방식)를 쓰지 않는 이유: /api/v1/auth 아래 다른 경로까지 가로챔.
     */
    private void startLogin(String registrationId,
                            HttpServletRequest request, HttpServletResponse response) throws IOException {
        OAuth2AuthorizationRequest authorizationRequest =
                authorizationRequestResolver.resolve(request, registrationId);
        authorizationRequestRepository.saveAuthorizationRequest(authorizationRequest, request, response);
        response.sendRedirect(authorizationRequest.getAuthorizationRequestUri());
    }

    /** 세션 폐기와 쿠키 만료. 현재 세션만 대상 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        sessionCookies.read(request).ifPresent(authSessionService::revoke);
        sessionCookies.expire(response);
        return ResponseEntity.noContent().build();
    }

    /** 현재 세션 외 같은 회원의 세션 전부 폐기. 인증 필요 — SecurityConfig에서 /auth/** 공개 규칙보다 앞에 지정 */
    @PostMapping("/logout-others")
    public LogoutOthersResponse logoutOthers(@AuthenticationPrincipal LoginMember loginMember,
                                             HttpServletRequest request) {
        AuthSession current = currentAuthSession.get(request)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        return new LogoutOthersResponse(authSessionService.revokeOthers(loginMember.memberId(), current.getId()));
    }
}
