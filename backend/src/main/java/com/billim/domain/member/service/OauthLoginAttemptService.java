package com.billim.domain.member.service;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.OauthLoginAttempt;
import com.billim.domain.member.entity.SocialProvider;
import com.billim.domain.member.repository.OauthLoginAttemptRepository;
import com.billim.global.security.session.SessionTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** OAuth state의 발급과 1회 소비 */
@Service
public class OauthLoginAttemptService {

    private final OauthLoginAttemptRepository oauthLoginAttemptRepository;
    private final Clock clock;
    private final Duration ttl;

    public OauthLoginAttemptService(OauthLoginAttemptRepository oauthLoginAttemptRepository,
                                    Clock clock,
                                    @Value("${billim.auth.oauth-state-ttl}") Duration ttl) {
        this.oauthLoginAttemptRepository = oauthLoginAttemptRepository;
        this.clock = clock;
        this.ttl = ttl;
    }

    /** 로그인 시작 시 state를 세션에 묶어 보관. state는 원문이 아닌 해시만 저장 */
    @Transactional
    public void start(AuthSession session, String state, String codeVerifier, String redirectUri) {
        Instant now = Instant.now(clock);
        oauthLoginAttemptRepository.save(OauthLoginAttempt.start(
                session, SessionTokens.hash(state), SocialProvider.KAKAO, now, ttl, codeVerifier, redirectUri));
    }

    /**
     * 콜백에서 state를 한 번만 소비. 성공 시 시작 당시의 기록 반환.
     * 단일 UPDATE로 소비해 동시 요청 중 하나만 통과. 조회 후 저장 방식은 둘 다 통과 가능.
     */
    @Transactional
    public OauthLoginAttempt consume(String state, Long sessionId) {
        String stateHash = SessionTokens.hash(state);
        if (oauthLoginAttemptRepository.consumeOnce(stateHash, sessionId, Instant.now(clock)) != 1) {
            return null;
        }
        return oauthLoginAttemptRepository.findWithSession(stateHash).orElse(null);
    }
}
