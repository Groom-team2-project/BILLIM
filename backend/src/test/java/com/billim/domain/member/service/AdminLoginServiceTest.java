package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 자격증명 대조와 비활성 판정 확인.
 * 대조 실수는 관리자 세션 탈취, 비활성 판정 실수는 운영에서 끈 엔드포인트 노출로 이어짐.
 */
class AdminLoginServiceTest {

    private static final String LOGIN_ID = "billim-admin";
    private static final String PASSWORD = "test-admin-pw";
    /** bcrypt(PASSWORD). 강도 4 — 테스트 속도 */
    private static final String PASSWORD_HASH = "$2y$04$lAfLhNzSpfrBolppuYsq1.Rz0gsTmlQySKyKTZfp1LFd34KU5FCq.";

    private final MemberAuthService memberAuthService = mock(MemberAuthService.class);
    private final AdminLoginService service = new AdminLoginService(memberAuthService, LOGIN_ID, PASSWORD_HASH);

    @Test
    @DisplayName("자격증명이 맞으면 관리자 회원을 돌려준다")
    void returnsAdminMemberOnValidCredentials() {
        Member admin = Member.registerAdmin("관리자");
        when(memberAuthService.loginOrRegisterLocalAdmin()).thenReturn(admin);

        assertThat(service.login(LOGIN_ID, PASSWORD)).isSameAs(admin);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401이고 회원 조회에 도달하지 않는다")
    void rejectsWrongPassword() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.login(LOGIN_ID, "wrong-password"))
                .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.UNAUTHENTICATED);
        verifyNoInteractions(memberAuthService);
    }

    @Test
    @DisplayName("ID가 틀리면 비밀번호가 맞아도 401")
    void rejectsWrongLoginId() {
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.login("other-admin", PASSWORD))
                .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    /** 운영에서 환경 변수를 빼면 꺼짐 — 존재 자체를 숨기는 404 */
    @Test
    @DisplayName("자격증명 설정이 하나라도 비면 올바른 값으로도 404")
    void disabledWhenCredentialsNotConfigured() {
        AdminLoginService noId = new AdminLoginService(memberAuthService, "", PASSWORD_HASH);
        AdminLoginService noHash = new AdminLoginService(memberAuthService, LOGIN_ID, "");

        for (AdminLoginService disabled : new AdminLoginService[]{noId, noHash}) {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> disabled.login(LOGIN_ID, PASSWORD))
                    .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
        verifyNoInteractions(memberAuthService);
    }

    /** 동시 첫 로그인 — UNIQUE 거부 후 재호출로 기존 회원을 받아야 500이 아님 */
    @Test
    @DisplayName("가입 경합으로 거부되면 한 번 재시도한다")
    void retriesOnConcurrentFirstLogin() {
        Member admin = Member.registerAdmin("관리자");
        when(memberAuthService.loginOrRegisterLocalAdmin())
                .thenThrow(new DataIntegrityViolationException("uk_social_accounts_provider_subject"))
                .thenReturn(admin);

        assertThat(service.login(LOGIN_ID, PASSWORD)).isSameAs(admin);
    }
}
