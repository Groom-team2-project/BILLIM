package com.billim.domain.item.controller;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/** 인증 주체는 members.id를 담는다 (LoginMember 계약) */
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
