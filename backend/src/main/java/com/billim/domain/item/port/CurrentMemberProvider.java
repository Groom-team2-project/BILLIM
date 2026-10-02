package com.billim.domain.item.port;

/**
 * 인증된 현재 회원 조회 계약. 구현은 회원·인증 담당(A) 영역.
 * TODO(A): 세션 인증 구현체 연결. 미인증이면 BusinessException(UNAUTHENTICATED)를 던져야 한다.
 */
public interface CurrentMemberProvider {

    /** 인증된 회원 ID. 미인증이면 UNAUTHENTICATED(401) */
    long requireMemberId();
}
