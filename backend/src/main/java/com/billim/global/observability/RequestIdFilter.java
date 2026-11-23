package com.billim.global.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청 추적 ID를 MDC와 응답 헤더에 싣는다.
 * 사용자가 받은 오류 응답의 requestId와 서버 로그를 잇는 열쇠다. (CONTRIBUTING 8 로그)
 *
 * <p>프록시·프론트엔드가 보낸 값이 있으면 이어 쓰고, 없거나 형식이 다르면 새로 만든다.
 * 로그에 그대로 남는 값이라 길이와 문자를 제한해 외부 입력이 로그를 어지럽히지 못하게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    /** 영숫자와 하이픈만. UUID와 대부분의 추적 ID 형식을 받아들인다 */
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 스레드를 재사용하므로 반드시 지운다
            MDC.remove(MDC_KEY);
        }
    }

    private String resolve(String incoming) {
        if (incoming != null && SAFE.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }

    /** 현재 요청의 추적 ID. 필터를 지나지 않은 호출(스케줄러 등)에서는 새 값을 만든다 */
    public static String current() {
        String requestId = MDC.get(MDC_KEY);
        return requestId == null ? UUID.randomUUID().toString() : requestId;
    }
}
