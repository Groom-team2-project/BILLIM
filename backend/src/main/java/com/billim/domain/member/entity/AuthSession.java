package com.billim.domain.member.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/** auth_sessions */
@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 전 CSRF·OAuth 세션은 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 쿠키 값의 SHA-256. 쿠키 원문 저장 금지 */
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(nullable = false, length = 64)
    private String csrfToken;

    @Column(nullable = false)
    private Instant lastSeenAt;

    /** 유휴 만료. absoluteExpiresAt 초과 금지 */
    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant absoluteExpiresAt;

    @Column
    private Instant revokedAt;

    private AuthSession(Member member, String tokenHash, String csrfToken,
                        Instant now, Duration idleTtl, Duration absoluteTtl) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.csrfToken = csrfToken;
        this.lastSeenAt = now;
        this.absoluteExpiresAt = now.plus(absoluteTtl);
        this.expiresAt = earlier(now.plus(idleTtl), this.absoluteExpiresAt);
    }

    /** 로그인 전 세션 */
    public static AuthSession anonymous(String tokenHash, String csrfToken,
                                        Instant now, Duration idleTtl, Duration absoluteTtl) {
        return new AuthSession(null, tokenHash, csrfToken, now, idleTtl, absoluteTtl);
    }

    /** 로그인 성공 시 회전해 새로 발급 */
    public static AuthSession forMember(Member member, String tokenHash, String csrfToken,
                                        Instant now, Duration idleTtl, Duration absoluteTtl) {
        return new AuthSession(member, tokenHash, csrfToken, now, idleTtl, absoluteTtl);
    }

    /** 요청마다 유휴 만료 연장 */
    public void touch(Instant now, Duration idleTtl) {
        this.lastSeenAt = now;
        this.expiresAt = earlier(now.plus(idleTtl), this.absoluteExpiresAt);
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt) && now.isBefore(absoluteExpiresAt);
    }

    public boolean isLoggedIn() {
        return member != null;
    }

    private static Instant earlier(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
