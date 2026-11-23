package com.billim.domain.member.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/** oauth_login_attempts */
@Entity
@Table(name = "oauth_login_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthLoginAttempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AuthSession session;

    /** 무작위 state의 SHA-256 */
    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    private String stateHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(nullable = false)
    private Instant expiresAt;

    /** 콜백에서 1회만 소비 */
    @Column
    private Instant consumedAt;

    /** PKCE 검증값. 난수라 재계산 불가하므로 보관. 미사용 제공자는 null */
    @Column(length = 128)
    private String codeVerifier;

    /** 인가 요청에 보낸 값. 콜백에서 동일해야 토큰 교환 성립 */
    @Column(nullable = false, length = 500)
    private String redirectUri;

    private OauthLoginAttempt(AuthSession session, String stateHash, SocialProvider provider,
                              Instant expiresAt, String codeVerifier, String redirectUri) {
        this.session = session;
        this.stateHash = stateHash;
        this.provider = provider;
        this.expiresAt = expiresAt;
        this.codeVerifier = codeVerifier;
        this.redirectUri = redirectUri;
    }

    /** 로그인 시작 시 state 발급 */
    public static OauthLoginAttempt start(AuthSession session, String stateHash, SocialProvider provider,
                                          Instant now, Duration ttl, String codeVerifier, String redirectUri) {
        return new OauthLoginAttempt(session, stateHash, provider, now.plus(ttl), codeVerifier, redirectUri);
    }

    public void consume(Instant now) {
        this.consumedAt = now;
    }

    public boolean isConsumable(Instant now) {
        return consumedAt == null && now.isBefore(expiresAt);
    }
}
