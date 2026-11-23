package com.billim.global.security;

import com.billim.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Netlify 서명 없는 /api 요청 거부. API 진입점을 Netlify 프록시 하나로 제한.
 * CloudFront 원본 주소·EC2 직접 호출로 레이트 리밋·프록시 헤더 처리를 우회하는 경로 차단.
 *
 * <p>대상 밖: /ws/chat(일회용 연결권 인증, 별도 CloudFront 진입) · 액추에이터(관리 포트).
 * 등록·활성 조건은 {@link com.billim.global.config.ProxySignatureConfig}.
 */
public class ProxySignatureFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";
    private static final String SIGNATURE_HEADER = "x-nf-sign";

    private final ProxySignatureVerifier verifier;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public ProxySignatureFilter(ProxySignatureVerifier verifier, SecurityErrorResponseWriter errorResponseWriter) {
        this.verifier = verifier;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!verifier.trusts(request.getHeader(SIGNATURE_HEADER))) {
            errorResponseWriter.write(response, ErrorCode.FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
