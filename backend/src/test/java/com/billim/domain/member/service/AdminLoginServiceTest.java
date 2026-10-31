package com.billim.domain.member.service;

import com.billim.domain.member.entity.Member;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    /** bcrypt(PASSWORD). 강도 10 — 동시 대조가 겹치도록 느린 해시 */
    private static final String SLOW_PASSWORD_HASH = "$2a$10$wXmlJ2PargR//cEz4O7GI.oXvkZTZd.8VEvmzAinRRZP3.PFJYpD6";
    private static final int MAX_FAILURES = 3;
    private static final Duration LOCK = Duration.ofMinutes(15);

    private final MemberAuthService memberAuthService = mock(MemberAuthService.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-29T00:00:00Z"));
    private final AdminLoginService service = serviceWith(LOGIN_ID, PASSWORD_HASH);

    private AdminLoginService serviceWith(String loginId, String passwordHash) {
        return new AdminLoginService(memberAuthService, new AdminLoginLockout(clock, MAX_FAILURES, LOCK),
                clock, loginId, passwordHash);
    }

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
        AdminLoginService noId = serviceWith("", PASSWORD_HASH);
        AdminLoginService noHash = serviceWith(LOGIN_ID, "");

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

    /** IP를 바꿔 가는 대입은 IP별 요청 제한을 통과 — 계정 단위로 막아야 함 */
    @Test
    @DisplayName("연속 실패가 한도에 닿으면 올바른 자격증명도 429로 거부한다")
    void locksAfterRepeatedFailures() {
        failTimes(MAX_FAILURES - 1);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.login(LOGIN_ID, "wrong-password"))
                .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.RATE_LIMITED);
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.login(LOGIN_ID, PASSWORD))
                .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.RATE_LIMITED);
        verify(memberAuthService, never()).loginOrRegisterLocalAdmin();
    }

    @Test
    @DisplayName("잠금 시간이 지나면 다시 로그인할 수 있다")
    void unlocksAfterLockDuration() {
        Member admin = Member.registerAdmin("관리자");
        when(memberAuthService.loginOrRegisterLocalAdmin()).thenReturn(admin);
        failTimes(MAX_FAILURES);

        clock.advance(LOCK);

        assertThat(service.login(LOGIN_ID, PASSWORD)).isSameAs(admin);
    }

    /** 오타 몇 번이 누적되어 다음 로그인 때 잠기지 않도록 */
    @Test
    @DisplayName("로그인에 성공하면 실패 누적이 초기화된다")
    void resetsFailuresOnSuccess() {
        Member admin = Member.registerAdmin("관리자");
        when(memberAuthService.loginOrRegisterLocalAdmin()).thenReturn(admin);
        failTimes(MAX_FAILURES - 1);
        service.login(LOGIN_ID, PASSWORD);

        failTimes(MAX_FAILURES - 1);

        assertThat(service.login(LOGIN_ID, PASSWORD)).isSameAs(admin);
    }

    @Test
    @DisplayName("집계 창이 지난 실패는 누적하지 않는다")
    void forgetsFailuresOutsideWindow() {
        Member admin = Member.registerAdmin("관리자");
        when(memberAuthService.loginOrRegisterLocalAdmin()).thenReturn(admin);
        failTimes(MAX_FAILURES - 1);

        clock.advance(LOCK);
        failTimes(MAX_FAILURES - 1);

        assertThat(service.login(LOGIN_ID, PASSWORD)).isSameAs(admin);
    }

    /** 고정 창이면 첫 실패 후 window가 지나는 순간 최근 실패까지 초기화 — 경계 양쪽 실패가 합산되지 않음 */
    @Test
    @DisplayName("실패는 현재 시각 기준 이동 창으로 집계한다")
    void countsFailuresInSlidingWindow() {
        failTimes(1);
        clock.advance(LOCK.minusSeconds(1));
        failTimes(1);
        clock.advance(Duration.ofSeconds(2));   // 첫 실패만 창 밖
        failTimes(1);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.login(LOGIN_ID, "wrong-password"))
                .extracting(BusinessException::getErrorCode).isEqualTo(ErrorCode.RATE_LIMITED);
    }

    /**
     * 확인과 기록이 분리되면 동시 요청이 모두 잠금 확인을 통과해 한도 이상 대조.
     * 대조가 겹치도록 느린 해시(cost 10) 사용
     */
    @Test
    @DisplayName("동시 요청도 한도를 넘겨 비밀번호를 대조하지 못한다")
    void concurrentAttemptsCannotExceedLimit() throws Exception {
        AdminLoginService slow = new AdminLoginService(memberAuthService,
                new AdminLoginLockout(clock, MAX_FAILURES, LOCK), clock, LOGIN_ID, SLOW_PASSWORD_HASH);
        int threads = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<ErrorCode>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    slow.login(LOGIN_ID, "wrong-password");
                    return null;
                } catch (BusinessException e) {
                    return e.getErrorCode();
                }
            }));
        }
        start.countDown();
        int verified = 0;
        for (Future<ErrorCode> result : results) {
            if (result.get() != ErrorCode.RATE_LIMITED) {
                verified++;
            }
        }
        pool.shutdown();

        // 한도 도달 실패는 429로 응답하므로 401은 한도 - 1 이하, 대조 자체는 한도 이하
        assertThat(verified).isLessThan(MAX_FAILURES);
    }

    @Test
    @DisplayName("잠금 한도가 0 이하인 설정은 기동을 거부한다")
    void rejectsNonPositiveLockoutSettings() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AdminLoginLockout(clock, 0, LOCK));
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AdminLoginLockout(clock, MAX_FAILURES, Duration.ZERO));
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> service.login(LOGIN_ID, "wrong-password"))
                    .extracting(BusinessException::getErrorCode)
                    .isIn(ErrorCode.UNAUTHENTICATED, ErrorCode.RATE_LIMITED);
        }
    }

    /** 경과 시간을 직접 조작하는 테스트용 Clock */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
