package com.billim.domain.item.controller;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 로그인 회원 ID 추출. Spring Security의 Authentication만 사용한다.
 * TODO(A): 인증 담당이 principal 형식(회원 ID를 담는 객체)을 확정하면 이 변환을 그 형식에 맞춘다.
 *          현재는 principal 이름이 회원 ID(숫자 문자열)라고 가정한다.
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
