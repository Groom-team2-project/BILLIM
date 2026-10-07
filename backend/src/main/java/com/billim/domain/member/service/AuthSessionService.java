package com.billim.domain.member.service;

import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.AuthSessionRepository;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.security.session.SessionTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** BILLIM_SESSION 세션의 발급·조회·갱신·폐기 */
@Service
public class AuthSessionService {

    private final AuthSessionRepository authSessionRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;
    private final Duration idleTtl;
    private final Duration absoluteTtl;
    private final Duration touchInterval;

    public AuthSessionService(AuthSessionRepository authSessionRepository,
                              MemberRepository memberRepository,
                              Clock clock,
                              @Value("${billim.auth.session-idle-ttl}") Duration idleTtl,
                              @Value("${billim.auth.session-absolute-ttl}") Duration absoluteTtl,
                              @Value("${billim.auth.session-touch-interval}") Duration touchInterval) {
        this.authSessionRepository = authSessionRepository;
        this.memberRepository = memberRepository;
        this.clock = clock;
        this.idleTtl = idleTtl;
        this.absoluteTtl = absoluteTtl;
        this.touchInterval = touchInterval;
    }

    /** 발급 결과. token은 쿠키에 실어 보내는 원문이며 DB에는 해시만 남게 됨 */
    public record Issued(String token, AuthSession session) {
    }

    /**
     * 로그인 전 세션. CSRF 토큰 발급과 OAuth state 보관에 사용.
     * csrfToken을 밖에서 받는 이유: Spring CsrfFilter가 만든 값을 그대로 보관해야 같은 요청의 응답과 일치.
     */
    @Transactional
    public Issued issueAnonymous(String csrfToken) {
        return issue(null, csrfToken);
    }

    /**
     * 로그인 성공 시 세션 회전. 기존 세션을 폐기하고 새 토큰으로 발급.
     * 엔티티가 아니라 쿠키 토큰을 받는 이유: 조회 시점의 트랜잭션이 끝나 분리된 상태라 변경이 반영되지 않기 때문.
     */
    @Transactional
    public Issued rotateForMember(String currentToken, Long memberId) {
        if (currentToken != null) {
            revoke(currentToken);
        }
        return issue(memberRepository.getReferenceById(memberId), SessionTokens.newToken());
    }

    /** 요청마다 쿠키로 세션 복원. 유휴 만료가 임박하면 연장. */
    @Transactional
    public Optional<AuthSession> loadUsable(String token) {
        Instant now = Instant.now(clock);
        return authSessionRepository.findWithMember(SessionTokens.hash(token))
                .filter(session -> session.isUsableAt(now))
                .map(session -> touchIfStale(session, now));
    }

    /** 로그아웃. 이미 폐기된 세션은 그대로 둠 */
    @Transactional
    public void revoke(String token) {
        authSessionRepository.findWithMember(SessionTokens.hash(token))
                .ifPresent(session -> session.revoke(Instant.now(clock)));
    }

    private Issued issue(Member member, String csrfToken) {
        Instant now = Instant.now(clock);
        String token = SessionTokens.newToken();
        String tokenHash = SessionTokens.hash(token);
        AuthSession session = member == null
                ? AuthSession.anonymous(tokenHash, csrfToken, now, idleTtl, absoluteTtl)
                : AuthSession.forMember(member, tokenHash, csrfToken, now, idleTtl, absoluteTtl);
        return new Issued(token, authSessionRepository.save(session));
    }

    /**
     * 매 요청 UPDATE를 피하기 위해 touchInterval이 지난 세션만 갱신.
     * 유휴 판정이 최대 touchInterval만큼 늦어지는 대신 쓰기 부하가 줄어듬.
     */
    private AuthSession touchIfStale(AuthSession session, Instant now) {
        if (session.getLastSeenAt().plus(touchInterval).isBefore(now)) {
            session.touch(now, idleTtl);
        }
        return session;
    }
}
