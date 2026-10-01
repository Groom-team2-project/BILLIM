-- members / social_accounts / auth_sessions / oauth_login_attempts

CREATE TABLE members
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    display_name        VARCHAR(30) NOT NULL COMMENT '2~30자 표시 이름. 중복 허용',
    role                VARCHAR(10) NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / WITHDRAWN. 이용 제한은 sanctions에서 판정',
    active_community_id BIGINT      NULL COMMENT '선택된 커뮤니티 (communities.id)',
    withdrawn_at        DATETIME(6) NULL COMMENT '탈퇴 정책 확정 후 사용',
    version             BIGINT      NOT NULL DEFAULT 0 COMMENT '낙관적 잠금',
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '회원 식별과 계정 상태';

CREATE TABLE social_accounts
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    member_id        BIGINT       NOT NULL COMMENT '회원',
    provider         VARCHAR(20)  NOT NULL COMMENT 'KAKAO. NAVER는 후순위',
    provider_subject VARCHAR(100) NOT NULL COLLATE utf8mb4_0900_as_cs COMMENT '제공자의 불변 식별자',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_accounts_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_social_accounts_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '소셜 제공자와 내부 회원 연결';

CREATE INDEX ix_social_accounts_member ON social_accounts (member_id);

CREATE TABLE auth_sessions
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    member_id           BIGINT      NULL COMMENT '로그인 전 CSRF/OAuth 세션은 NULL',
    token_hash          CHAR(64)    NOT NULL COMMENT '쿠키 값의 SHA-256. 쿠키 원문 저장 금지',
    csrf_token          VARCHAR(64) NOT NULL COMMENT '세션에 결합한 무작위 CSRF 토큰',
    last_seen_at        DATETIME(6) NOT NULL COMMENT '유휴 만료 판정 시각',
    expires_at          DATETIME(6) NOT NULL COMMENT '유휴 만료. 절대 만료 초과 금지',
    absolute_expires_at DATETIME(6) NOT NULL COMMENT '로그인 시각 + 24시간',
    revoked_at          DATETIME(6) NULL COMMENT '로그아웃/회전 폐기 시각',
    created_at          DATETIME(6) NOT NULL,
    updated_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_sessions_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT '쿠키 세션 저장소';

CREATE INDEX ix_auth_sessions_member_revoked ON auth_sessions (member_id, revoked_at);
CREATE INDEX ix_auth_sessions_expires ON auth_sessions (expires_at);

CREATE TABLE oauth_login_attempts
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL COMMENT '로그인 전 세션',
    state_hash  CHAR(64)    NOT NULL COMMENT '무작위 state의 해시',
    provider    VARCHAR(20) NOT NULL COMMENT 'KAKAO',
    expires_at  DATETIME(6) NOT NULL COMMENT '발급 후 10분',
    consumed_at DATETIME(6) NULL COMMENT '콜백에서 원자적으로 한 번 소비',
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_oauth_login_attempts_state_hash UNIQUE (state_hash),
    CONSTRAINT fk_oauth_login_attempts_session FOREIGN KEY (session_id) REFERENCES auth_sessions (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT 'OAuth state와 브라우저 세션 결합';

CREATE INDEX ix_oauth_login_attempts_expires ON oauth_login_attempts (expires_at);
