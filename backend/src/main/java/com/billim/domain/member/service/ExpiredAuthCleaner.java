package com.billim.domain.member.service;

import com.billim.domain.member.repository.AuthSessionRepository;
import com.billim.domain.member.repository.OauthLoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.IntUnaryOperator;

/**
 * 만료된 세션과 OAuth 시도 정리.
 * Redis와 달리 DB는 TTL 자동 삭제가 없어 직접 지워야 한다. 방치하면 조회가 느려진다.
 */
@Component
public class ExpiredAuthCleaner {

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthCleaner.class);

    /** 묶음 반복 한도 — 삭제 중 새 만료가 계속 생겨도 한 주기가 무한정 돌지 않게 하는 상한 */
    private static final int MAX_ROUNDS = 1_000;

    private final AuthSessionRepository authSessionRepository;
    private final OauthLoginAttemptRepository oauthLoginAttemptRepository;
    private final Clock clock;
    private final Duration retention;
    private final int chunkSize;
    private final long chunkPauseMs;

    public ExpiredAuthCleaner(AuthSessionRepository authSessionRepository,
                              OauthLoginAttemptRepository oauthLoginAttemptRepository,
                              Clock clock,
                              @Value("${billim.auth.cleanup-retention}") Duration retention,
                              @Value("${billim.auth.cleanup-chunk-size}") int chunkSize,
                              @Value("${billim.auth.cleanup-chunk-pause-ms}") long chunkPauseMs) {
        // 0 이하면 LIMIT 0 삭제가 종료 조건(deleted < chunkSize)에 걸리지 않아 정리가 조용히 꺼짐 — 기동 시점 거부
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("billim.auth.cleanup-chunk-size는 양수 필요: " + chunkSize);
        }
        this.authSessionRepository = authSessionRepository;
        this.oauthLoginAttemptRepository = oauthLoginAttemptRepository;
        this.clock = clock;
        this.retention = retention;
        this.chunkSize = chunkSize;
        this.chunkPauseMs = chunkPauseMs;
    }

    /**
     * 삭제 순서 고정. oauth_login_attempts가 auth_sessions를 FK로 참조하고 삭제 규칙이 RESTRICT라
     * 세션을 먼저 지우면 제약 위반.
     *
     * <p>메서드 전체를 한 트랜잭션으로 묶지 않음. 인증은 모든 요청이 auth_sessions를 조회하므로
     * 커밋까지 잠금이 유지되면 정리 중 응답시간 상승. (성능 측정 5절 — 삭제 초 처리량 75%)
     *
     * <p>다중 인스턴스에서 동시 실행 가능 — DELETE라 결과는 동일.
     */
    @Scheduled(cron = "${billim.auth.cleanup-cron}")
    public void purgeExpired() {
        Instant threshold = Instant.now(clock).minus(retention);
        int attempts = purgeInChunks(chunk -> oauthLoginAttemptRepository.deleteExpiredBefore(threshold, chunk));
        int sessions = purgeInChunks(chunk -> authSessionRepository.deleteExpiredBefore(threshold, chunk));
        if (attempts > 0 || sessions > 0) {
            log.info("만료 정리 — oauth_login_attempts {}건, auth_sessions {}건", attempts, sessions);
        }
    }

    /**
     * 묶음이 다 차지 않을 때까지 반복. 묶음 하나가 트랜잭션 하나.
     * 사이에 잠깐 비우는 것은 커넥션 풀을 정리 작업이 독점하지 않게 하려는 것.
     */
    private int purgeInChunks(IntUnaryOperator deleteChunk) {
        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            // 셧다운 등 인터럽트 수신 — 남은 묶음과 다음 테이블 중단, 다음 주기에 이어서 정리
            if (Thread.currentThread().isInterrupted()) {
                log.warn("만료 정리 중단 — 인터럽트 수신, 누적 {}건", total);
                return total;
            }
            int deleted = deleteChunk.applyAsInt(chunkSize);
            total += deleted;
            if (deleted < chunkSize) {
                return total;
            }
            pause();
        }
        log.warn("만료 정리 미완 — 묶음 {}회 한도 도달, 누적 {}건. 다음 주기에 이어서 정리", MAX_ROUNDS, total);
        return total;
    }

    private void pause() {
        if (chunkPauseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(chunkPauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
