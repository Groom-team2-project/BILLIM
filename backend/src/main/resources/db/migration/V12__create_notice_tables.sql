-- 커뮤니티 공지. 도메인 및 DB 설계 4.7 기준.
-- 본문은 일반 텍스트로 렌더링한다. 임의 HTML 실행 금지는 화면 계약이다. (API 명세 E_066)

CREATE TABLE notices (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    community_id BIGINT       NOT NULL COMMENT '대상 커뮤니티',
    author_id    BIGINT       NOT NULL COMMENT '관리자',
    title        VARCHAR(150) NOT NULL COMMENT '1~150자',
    body         TEXT         NOT NULL COMMENT '1~10000자 일반 텍스트',
    pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at   DATETIME(6)  NULL COMMENT '논리 삭제',
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notices_community_deleted_pinned (community_id, deleted_at, pinned, created_at, id),
    CONSTRAINT fk_notices_community FOREIGN KEY (community_id) REFERENCES communities (id),
    CONSTRAINT fk_notices_author FOREIGN KEY (author_id) REFERENCES members (id),
    CONSTRAINT ck_notices_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_notices_body CHECK (CHAR_LENGTH(TRIM(body)) BETWEEN 1 AND 10000)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '커뮤니티 공지';
