package com.billim.domain.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 관리자 전용 로그인의 계정 단위 실패 잠금.
 * IP별 요청 제한(AuthRateLimitFilter)은 주소를 바꿔 가는 대입을 막지 못함 — 계정이 하나라 계정 기준으로 집계.
 * 잠금 중에는 bcrypt 대조를 생략해 대입 시도의 CPU 소모도 차단.
 *
 * <p>상태는 인스턴스 메모리 — 서버 대수만큼 허용 실패 수 증가. 단일 계정 대입 차단 목적상 허용 범위.
 * 잠금 동안은 정상 관리자도 대기 — 대입 성공 위험보다 작은 비용으로 판단.
 */
@Component
public class AdminLoginLockout {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    private int failures;
    private Instant firstFailureAt;
    private Instant lockedUntil;

    public AdminLoginLockout(Clock clock,
                             @Value("${billim.auth.admin-login-max-failures}") int maxFailures,
                             @Value("${billim.auth.admin-login-lock-duration}") Duration window) {
        // 0 이하면 첫 실패 전부터 잠금 판정이 어긋남 — 기동 시점 거부
        if (maxFailures <= 0 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("billim.auth.admin-login-* 값은 양수 필요: max-failures=%d, lock-duration=%s"
                    .formatted(maxFailures, window));
        }
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
    }

    /** 잠금 중이면 해제 시각 */
    public synchronized Optional<Instant> lockedUntil() {
        Instant now = Instant.now(clock);
        if (lockedUntil != null && now.isBefore(lockedUntil)) {
            return Optional.of(lockedUntil);
        }
        lockedUntil = null;
        return Optional.empty();
    }

    /**
     * 실패 기록. 집계 창(window) 안의 실패만 누적, 한도 도달 시 window만큼 잠금.
     * @return 잠금 시작 시 해제 시각
     */
    public synchronized Optional<Instant> recordFailure() {
        Instant now = Instant.now(clock);
        if (firstFailureAt == null || !now.isBefore(firstFailureAt.plus(window))) {
            failures = 0;
            firstFailureAt = now;
        }
        failures++;
        if (failures < maxFailures) {
            return Optional.empty();
        }
        failures = 0;
        firstFailureAt = null;
        lockedUntil = now.plus(window);
        return Optional.of(lockedUntil);
    }

    public synchronized int failures() {
        return failures;
    }

    /** 성공 시 누적 초기화 — 오타 몇 번이 다음 로그인 잠금으로 이어지지 않도록 */
    public synchronized void reset() {
        failures = 0;
        firstFailureAt = null;
    }
}
