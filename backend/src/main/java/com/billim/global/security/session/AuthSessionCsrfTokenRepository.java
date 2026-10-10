package com.billim.global.security.session;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Component;

/**
 * CSRF 토큰을 HTTP 세션이 아니라 auth_sessions.csrf_token에서 읽음.
 * 토큰은 세션과 수명을 공유하며 세션이 폐기·회전되면 같이 무효가 됨.
 */
@Component
@RequiredArgsConstructor
public class AuthSessionCsrfTokenRepository implements CsrfTokenRepository {

    /** 프론트엔드 api/client.ts가 보내는 헤더 */
    private static final String HEADER_NAME = "X-CSRF-TOKEN";
    private static final String PARAMETER_NAME = "_csrf";

    private final CurrentAuthSession currentAuthSession;
    private final AuthSessionService authSessionService;
    private final SessionCookies sessionCookies;

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return currentAuthSession.get(request)
                .map(AuthSession::getCsrfToken)
                .map(this::toToken)
                .orElse(null);
    }

    /** loadToken이 null일 때만 호출됨. 값은 saveToken에서 세션과 함께 저장 */
    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return toToken(SessionTokens.newToken());
    }

    /**
     * 로그인 전 세션 발급 지점. (BILLIM_SESSION 쿠키 발급)
     * Spring이 토큰을 새로 만들 때 response를 함께 넘겨주므로 여기서 쿠키를 내려보냄.
     * GET 제한: 세션 없는 변경 요청도 여길 지나는데, 그때 만드는 세션은
     * 방금 생성된 토큰과 요청의 토큰이 일치할 수 없어 403 확정이라 쓸모없는 행만 남김.
     */
    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        if (token == null || !"GET".equals(request.getMethod())
                || currentAuthSession.get(request).isPresent()) {
            return;
        }
        AuthSessionService.Issued issued = authSessionService.issueAnonymous(token.getToken());
        sessionCookies.write(response, issued.token());
        currentAuthSession.set(request, issued.session());
    }

    private CsrfToken toToken(String value) {
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, value);
    }
}
