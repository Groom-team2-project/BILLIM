package com.billim.global.security.session;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 요청 하나 안에서 세션 조회를 한 번으로 제한.
 * 인증 복원과 CSRF 토큰 조회가 같은 세션을 쓰므로 요청 속성에 결과를 보관.
 */
@Component
@RequiredArgsConstructor
public class CurrentAuthSession {

    private static final String ATTRIBUTE = CurrentAuthSession.class.getName();

    private final SessionCookies sessionCookies;
    private final AuthSessionService authSessionService;

    public Optional<AuthSession> get(HttpServletRequest request) {
        if (request.getAttribute(ATTRIBUTE) instanceof Cached cached) {
            return Optional.ofNullable(cached.session());
        }
        Optional<AuthSession> loaded = sessionCookies.read(request)
                .flatMap(authSessionService::loadUsable);
        request.setAttribute(ATTRIBUTE, new Cached(loaded.orElse(null)));
        return loaded;
    }

    /** 발급·회전 직후 같은 요청에서 새 세션이 보이도록 교체 */
    public void set(HttpServletRequest request, AuthSession session) {
        request.setAttribute(ATTRIBUTE, new Cached(session));
    }

    /** 세션 없음도 캐시해야 재조회를 막음. Optional 캐스팅을 피하기 위해 레코드로 감쌈. */
    private record Cached(AuthSession session) {
    }
}
