package com.billim.global.security.oauth;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.OauthLoginAttempt;
import com.billim.domain.member.service.AuthSessionService;
import com.billim.domain.member.service.OauthLoginAttemptService;
import com.billim.global.security.session.CurrentAuthSession;
import com.billim.global.security.session.SessionCookies;
import com.billim.global.security.session.SessionTokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.stereotype.Component;

/**
 * 인가 요청을 HTTP 세션이 아닌 oauth_login_attempts에 보관.
 * 로그인 시작과 콜백이 다른 인스턴스로 가도 state 검증과 토큰 교환 성립.
 */
@Component
@RequiredArgsConstructor
public class DbAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String REGISTRATION_ID = "kakao";

    private final OauthLoginAttemptService oauthLoginAttemptService;
    private final AuthSessionService authSessionService;
    private final CurrentAuthSession currentAuthSession;
    private final SessionCookies sessionCookies;
    private final ClientRegistrationRepository clientRegistrationRepository;

    /** 로그인 시작. 세션이 없으면 여기서 발급 */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            return;   // 제거 신호. 소비는 removeAuthorizationRequest 담당
        }
        AuthSession session = currentAuthSession.get(request)
                .orElseGet(() -> issueSession(request, response));
        oauthLoginAttemptService.start(session,
                authorizationRequest.getState(),
                authorizationRequest.getAttribute(PkceParameterNames.CODE_VERIFIER),
                authorizationRequest.getRedirectUri());
    }

    /** 소비 없는 조회. 현재 로그인 흐름에서는 호출되지 않으나 계약상 구현 */
    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return null;
    }

    /**
     * 콜백. 시작한 세션에서만 state를 1회 소비하고 그 기록으로 인가 요청 재구성.
     * 세션 조건을 소비 쿼리에 넣어 검증과 소비를 한 번에 처리.
     */
    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        AuthSession session = currentAuthSession.get(request).orElse(null);
        if (state == null || session == null) {
            return null;
        }
        OauthLoginAttempt attempt = oauthLoginAttemptService.consume(state, session.getId());
        return attempt == null ? null : rebuild(state, attempt);
    }

    /**
     * 설정에서 알 수 있는 값은 ClientRegistration에서, 난수인 값만 DB에서 가져와 조립.
     * code_verifier는 재계산이 불가해 저장이 필요한 유일한 값.
     */
    private OAuth2AuthorizationRequest rebuild(String state, OauthLoginAttempt attempt) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID);
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId())
                .redirectUri(attempt.getRedirectUri())
                .scopes(registration.getScopes())
                .state(state)
                .attributes(attributes -> {
                    attributes.put(OAuth2ParameterNames.REGISTRATION_ID, registration.getRegistrationId());
                    if (attempt.getCodeVerifier() != null) {
                        attributes.put(PkceParameterNames.CODE_VERIFIER, attempt.getCodeVerifier());
                    }
                })
                .build();
    }

    private AuthSession issueSession(HttpServletRequest request, HttpServletResponse response) {
        AuthSessionService.Issued issued = authSessionService.issueAnonymous(SessionTokens.newToken());
        sessionCookies.write(response, issued.token());
        currentAuthSession.set(request, issued.session());
        return issued.session();
    }
}
