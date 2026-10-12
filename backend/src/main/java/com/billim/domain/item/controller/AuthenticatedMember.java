package com.billim.domain.item.controller;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 로그인 회원 ID 추출. Spring Security의 Authentication만 사용한다.
 * 계약: 인증 주체(LoginMember)의 getName()이 members.id를 돌려준다 (global/security/LoginMember 참고).
 */
final class AuthenticatedMember {

    private AuthenticatedMember() {
    }

    /** 미인증이거나 회원 ID를 읽을 수 없으면 401 UNAUTHENTICATED */
    static long id(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
