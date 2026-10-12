package com.billim.domain.safety.controller;

import com.billim.domain.member.entity.MemberRole;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;

/** 안전·운영 컨트롤러가 공유하는 인증 주체 해석과 헤더 형식 */
final class SafetyRequests {

    /** POST의 Idempotency-Key 형식 (명세 0.3: UUID 필수) */
    static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private SafetyRequests() {
    }

    /** 인증 주체는 members.id를 담는다 (LoginMember 계약) */
    static long memberId(LoginMember member) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return member.memberId();
    }

    /**
     * 관리자 API의 전역 ADMIN 역할 확인 (명세 0.2).
     * 역할이 아니면 403으로 막는다. 모든 /api/v1/admin 진입점이 이 검사를 먼저 거친다.
     *
     * <p>TODO(A 인증): SecurityFilterChain에 {@code /api/v1/admin/** → hasRole("ADMIN")}을 더하면
     * 경로 단위로도 막힌다. 보안 설정 변경이라 인증 담당과 합의한 뒤 적용한다.
     * 그때도 이 검사는 남긴다 — 화면·설정이 아니라 서버에서 권한을 검증한다는 원칙(기획서 A 어필).
     */
    static long adminId(LoginMember member) {
        long memberId = memberId(member);
        if (member.role() != MemberRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return memberId;
    }
}
