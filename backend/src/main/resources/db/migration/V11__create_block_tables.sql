-- 방향성 차단 관계. 도메인 및 DB 설계 4.7 기준.
-- 관계 판정은 양방향 OR이고, 해제는 해당 방향 행만 지운다.

CREATE TABLE blocks (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    blocker_id BIGINT      NOT NULL COMMENT '차단 실행자',
    blocked_id BIGINT      NOT NULL COMMENT '차단 대상',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_blocks_blocker_blocked (blocker_id, blocked_id),
    -- 나를 차단한 사람을 찾는 역방향 조회용
    KEY idx_blocks_blocked_blocker (blocked_id, blocker_id),
    CONSTRAINT fk_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES members (id),
    CONSTRAINT fk_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES members (id),
    CONSTRAINT ck_blocks_not_self CHECK (blocker_id <> blocked_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '방향성 차단 관계';
