package com.billim.domain.member.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 관리자 전용 로그인의 계정 단위 실패 잠금.
 * IP별 요청 제한(AuthRateLimitFilter)은 주소를 바꿔 가는 대입을 막지 못함 — 계정이 하나라 계정 기준으로 집계.
 * 잠금 중에는 bcrypt 대조를 생략해 대입 시도의 CPU 소모도 차단.
 *
 * <p>시도 슬롯 선점: 대조 전에 begin()으로 슬롯 확보, 결과는 fail()·succeed()로 확정.
 * 확인과 기록이 분리되면 동시 요청이 모두 확인을 통과해 한도 이상 대조 가능.
 * 진행 중 시도 + 최근 실패가 한도에 닿으면 대조 없이 거부. 잠금(시간 차단)은 실패가 한도에 닿을 때만.
 *
 * <p>집계는 이동 창 — 현재 시각 기준 window 안의 실패만 계수. 고정 창은 경계 양쪽 실패가 합산되지 않음.
 *
 * <p>상태는 인스턴스 메모리 — 서버 대수만큼 허용 실패 수 증가. 단일 계정 대입 차단 목적상 허용 범위.
 * 잠금 동안은 정상 관리자도 대기 — 대입 성공 위험보다 작은 비용으로 판단.
 */
@Component
public class AdminLoginLockout {

    private final Clock clock;
    private final int maxFailures;
    private final Duration window;

    private final Deque<Instant> failures = new ArrayDeque<>();
    private int inFlight;
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

    /**
     * 시도 슬롯 확보. 거부 시 재시도 가능 시각 반환 — 잠금 중이면 해제 시각, 동시 시도 포화면 현재 시각.
     * 슬롯을 확보했으면 반드시 fail() 또는 succeed()로 확정.
     */
    public synchronized Optional<Instant> begin() {
        Instant now = Instant.now(clock);
        if (lockedUntil != null && now.isBefore(lockedUntil)) {
            return Optional.of(lockedUntil);
        }
        lockedUntil = null;
        prune(now);
        if (failures.size() + inFlight >= maxFailures) {
            return Optional.of(now);
        }
        inFlight++;
        return Optional.empty();
    }

    /**
     * 확보한 슬롯을 실패로 확정. 창 안의 실패가 한도에 닿으면 window만큼 잠금.
     * @return 잠금 시작 시 해제 시각
     */
    public synchronized Optional<Instant> fail() {
        release();
        Instant now = Instant.now(clock);
        prune(now);
        failures.addLast(now);
        if (failures.size() < maxFailures) {
            return Optional.empty();
        }
        failures.clear();
        lockedUntil = now.plus(window);
        return Optional.of(lockedUntil);
    }

    /** 확보한 슬롯을 성공으로 확정. 실패 누적 초기화 — 오타 몇 번이 다음 로그인 잠금으로 이어지지 않도록 */
    public synchronized void succeed() {
        release();
        failures.clear();
    }

    /** 창 안의 실패 수 — 로그용 */
    public synchronized int failures() {
        prune(Instant.now(clock));
        return failures.size();
    }

    private void release() {
        if (inFlight > 0) {
            inFlight--;
        }
    }

    private void prune(Instant now) {
        Instant threshold = now.minus(window);
        while (!failures.isEmpty() && !failures.peekFirst().isAfter(threshold)) {
            failures.removeFirst();
        }
    }
}
