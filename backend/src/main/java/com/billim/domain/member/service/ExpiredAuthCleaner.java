package com.billim.domain.member.service;

import com.billim.domain.member.repository.AuthSessionRepository;
import com.billim.domain.member.repository.OauthLoginAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 만료된 세션과 OAuth 시도 정리.
 * Redis와 달리 DB는 TTL 자동 삭제가 없어 직접 지워야 한다. 방치하면 조회가 느려진다.
 */
@Component
public class ExpiredAuthCleaner {

    private static final Logger log = LoggerFactory.getLogger(ExpiredAuthCleaner.class);

    private final AuthSessionRepository authSessionRepository;
    private final OauthLoginAttemptRepository oauthLoginAttemptRepository;
    private final Clock clock;
    private final Duration retention;

    public ExpiredAuthCleaner(AuthSessionRepository authSessionRepository,
                              OauthLoginAttemptRepository oauthLoginAttemptRepository,
                              Clock clock,
                              @Value("${billim.auth.cleanup-retention}") Duration retention) {
        this.authSessionRepository = authSessionRepository;
        this.oauthLoginAttemptRepository = oauthLoginAttemptRepository;
        this.clock = clock;
        this.retention = retention;
    }

    /**
     * 삭제 순서 고정. oauth_login_attempts가 auth_sessions를 FK로 참조하고 삭제 규칙이 RESTRICT라
     * 세션을 먼저 지우면 실패한다.
     * 다중 인스턴스에서 동시에 돌 수 있으나 DELETE라 결과는 같다. 부하가 문제되면 분산 잠금 도입.
     */
    @Scheduled(cron = "${billim.auth.cleanup-cron}")
    @Transactional
    public void purgeExpired() {
        Instant threshold = Instant.now(clock).minus(retention);
        int attempts = oauthLoginAttemptRepository.deleteExpiredBefore(threshold);
        int sessions = authSessionRepository.deleteExpiredBefore(threshold);
        if (attempts > 0 || sessions > 0) {
            log.info("만료 정리 — oauth_login_attempts {}건, auth_sessions {}건", attempts, sessions);
        }
    }
}
