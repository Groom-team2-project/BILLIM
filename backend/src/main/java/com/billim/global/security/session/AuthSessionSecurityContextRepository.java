package com.billim.global.security.session;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.Member;
import com.billim.global.security.LoginMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * HTTP 세션 대신 auth_sessions로 인증을 복원.
 * 서버 메모리에 의존하지 않으므로 재시작과 다중 인스턴스에서 로그인이 유지됨.
 */
@Component
@RequiredArgsConstructor
public class AuthSessionSecurityContextRepository implements SecurityContextRepository {

    /** 카카오 등록 ID. OAuth2AuthenticationToken 복원에 필요 */
    private static final String REGISTRATION_ID = "kakao";

    private final CurrentAuthSession currentAuthSession;

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        return new DeferredSecurityContext() {
            private SecurityContext context;

            @Override
            public SecurityContext get() {
                if (context == null) {
                    context = restore(request);
                }
                return context;
            }

            /** 저장된 인증이 없어 빈 컨텍스트를 만들어 준 경우 true */
            @Override
            public boolean isGenerated() {
                return get().getAuthentication() == null;
            }
        };
    }

    @Override
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        return restore(requestResponseHolder.getRequest());
    }

    /**
     * 저장하지 않음. 세션 생성은 CSRF 토큰 저장소와 로그인 성공 핸들러가 명시적으로 수행.
     * 매 요청 자동 저장에 맡기면 익명 요청마다 세션 행이 쌓이는 문제.
     */
    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        return currentAuthSession.get(request).filter(AuthSession::isLoggedIn).isPresent();
    }

    private SecurityContext restore(HttpServletRequest request) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        currentAuthSession.get(request)
                .filter(AuthSession::isLoggedIn)
                .map(AuthSession::getMember)
                .ifPresent(member -> context.setAuthentication(toAuthentication(member)));
        return context;
    }

    private OAuth2AuthenticationToken toAuthentication(Member member) {
        LoginMember principal = new LoginMember(member.getId(), member.getRole());
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), REGISTRATION_ID);
    }
}
