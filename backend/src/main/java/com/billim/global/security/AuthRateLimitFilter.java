package com.billim.global.security;

import com.billim.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 익명 인증 엔드포인트의 IP별 요청 제한. 고정 윈도우(1분), 초과 시 429 RATE_LIMITED.
 * /auth/csrf는 auth_sessions, /auth/kakao·naver는 oauth_login_attempts에 익명 요청마다
 * 행이 생겨 무제한 허용 시 DB 쓰기 남용 가능. (에러 코드 RATE_LIMITED)
 * /auth/admin/login은 DB 쓰기가 아니라 자격증명 무차별 대입 방어 목적.
 *
 * <p>카운터는 인스턴스별 메모리 — 서버 대수만큼 실효 한도가 늘지만 남용 차단 목적에는 허용 범위.
 * 인스턴스 간 공유가 필요해지면 기존 Redis로 이전.
 *
 * <p>등록·순서는 {@link com.billim.global.config.AuthRateLimitConfig} — 컴포넌트가 아닌 이유도 거기에.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final String CSRF_PATH = "/api/v1/auth/csrf";
    private static final Set<String> LOGIN_PATHS =
            Set.of("/api/v1/auth/kakao", "/api/v1/auth/naver", "/api/v1/auth/admin/login");
    /** 윈도우 길이(초). 설정 항목 이름(…-per-minute)에 고정 */
    private static final long WINDOW_SECONDS = 60;
    /** IPv6 최대 표기 길이. 넘는 헤더 값은 위조로 보고 접속 주소로 대체 */
    private static final int MAX_IP_LENGTH = 45;
    /** Netlify 서명 프록시가 싣는 JWS 헤더 */
    private static final String PROXY_SIGNATURE_HEADER = "x-nf-sign";

    private final SecurityErrorResponseWriter errorResponseWriter;
    private final Clock clock;
    private final String clientIpHeader;
    private final ProxySignatureVerifier proxySignatureVerifier;
    private final int csrfPerMinute;
    private final int loginPerMinute;
    private final int maxTrackedKeys;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    /** 마지막으로 지난 윈도우를 정리한 분. 초기값은 어떤 분과도 다르게 */
    private final AtomicLong lastSweptMinute = new AtomicLong(Long.MIN_VALUE);

    public AuthRateLimitFilter(SecurityErrorResponseWriter errorResponseWriter,
                               Clock clock,
                               String clientIpHeader,
                               ProxySignatureVerifier proxySignatureVerifier,
                               int csrfPerMinute,
                               int loginPerMinute,
                               int maxTrackedKeys) {
        // 0 이하면 모든 로그인 진입이 차단 — 기동 시점 거부. 끄려면 rate-limit-enabled 사용
        if (csrfPerMinute <= 0 || loginPerMinute <= 0 || maxTrackedKeys <= 0) {
            throw new IllegalArgumentException("billim.auth.rate-limit-* 값은 양수 필요: csrf=%d, login=%d, max-tracked=%d"
                    .formatted(csrfPerMinute, loginPerMinute, maxTrackedKeys));
        }
        this.errorResponseWriter = errorResponseWriter;
        this.clock = clock;
        this.clientIpHeader = clientIpHeader;
        this.proxySignatureVerifier = proxySignatureVerifier;
        this.csrfPerMinute = csrfPerMinute;
        this.loginPerMinute = loginPerMinute;
        this.maxTrackedKeys = maxTrackedKeys;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean csrf = CSRF_PATH.equals(uri);
        boolean login = LOGIN_PATHS.contains(uri);
        if (!csrf && !login) {
            filterChain.doFilter(request, response);
            return;
        }
        // 그룹을 키에 포함 — kakao·naver를 번갈아 불러도 로그인 허용량은 하나
        String key = (csrf ? "csrf:" : "login:") + clientIp(request);
        if (!tryAcquire(key, csrf ? csrfPerMinute : loginPerMinute)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean tryAcquire(String key, int limit) {
        long minute = clock.instant().getEpochSecond() / WINDOW_SECONDS;
        if (overloaded(key, minute)) {
            return false;
        }
        Window window = windows.compute(key,
                (k, current) -> current == null || current.minute != minute ? new Window(minute) : current);
        return window.count.incrementAndGet() <= limit;
    }

    /**
     * 추적 항목 상한 — 위조 헤더로 키를 불려도 메모리 유한.
     * 지난 윈도우를 정리해도 가득하면 새 키만 차단. DB 보호가 목적이라 이때는 거부가 허용보다 안전.
     * 정리는 윈도우당 한 번 — 상한 상태에서 요청마다 맵 전체를 순회하지 않도록
     */
    private boolean overloaded(String key, long minute) {
        if (windows.size() < maxTrackedKeys || windows.containsKey(key)) {
            return false;
        }
        long swept = lastSweptMinute.get();
        if (swept != minute && lastSweptMinute.compareAndSet(swept, minute)) {
            windows.values().removeIf(window -> window.minute != minute);
        }
        if (windows.size() < maxTrackedKeys) {
            return false;
        }
        log.warn("요청 제한 추적 상한 도달 — 새 주소 차단 중. 추적 {}건", windows.size());
        return true;
    }

    /**
     * 운영은 Netlify 프록시 뒤라 접속 주소가 프록시 IP — 실제 클라이언트는 프록시가 싣는 헤더로 식별.
     * X-Forwarded-For가 아닌 이유: ForwardedHeaderFilter(forward-headers-strategy=framework)가
     * X-Forwarded-*를 하위 필터에서 숨겨 여기서는 읽을 수 없음. 프록시 교체 시 헤더 이름은 설정으로 변경.
     * 프록시 서명이 없으면 직접 호출로 보고 헤더 무시 — 헤더를 바꿔 가며 제한 우회 방지
     */
    private String clientIp(HttpServletRequest request) {
        String fromProxy = request.getHeader(clientIpHeader);
        if (fromProxy == null || fromProxy.isBlank() || fromProxy.length() > MAX_IP_LENGTH
                || !proxySignatureVerifier.trusts(request.getHeader(PROXY_SIGNATURE_HEADER))) {
            return connectionAddress(request);
        }
        return fromProxy.trim();
    }

    /**
     * 래퍼 아래 원본 요청의 접속 주소. ForwardedHeaderFilter 래퍼의 getRemoteAddr()는
     * X-Forwarded-For 값을 반환 — 직접 호출자가 헤더를 바꿔 가며 제한 우회 가능.
     * 필터 순서가 아닌 언래핑으로 처리 — 등록 순서 변경에도 유지.
     */
    private static String connectionAddress(ServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper) {
            current = wrapper.getRequest();
        }
        return current.getRemoteAddr();
    }

    private void reject(HttpServletResponse response) throws IOException {
        long secondsToNextWindow = WINDOW_SECONDS - clock.instant().getEpochSecond() % WINDOW_SECONDS;
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondsToNextWindow));
        errorResponseWriter.write(response, ErrorCode.RATE_LIMITED);
    }

    /** 윈도우 하나의 카운터. 분이 바뀌면 compute()가 새 항목으로 교체 */
    private static final class Window {
        final long minute;
        final AtomicInteger count = new AtomicInteger();

        Window(long minute) {
            this.minute = minute;
        }
    }
}
