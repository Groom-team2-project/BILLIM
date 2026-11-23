-- 신고·처리 이력·제재 테이블. 도메인 및 DB 설계 4.7 기준.
-- reports.community_id는 communities 테이블이 생긴 뒤 FK 추가 필요. (V1·V6와 같은 보류 FK)

CREATE TABLE reports (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id       BIGINT        NOT NULL COMMENT '신고자',
    target_type       VARCHAR(10)   NOT NULL COMMENT 'MEMBER / ITEM / MESSAGE',
    target_member_id  BIGINT        NOT NULL COMMENT '서버가 계산한 처리 대상 회원',
    target_item_id    BIGINT        NULL COMMENT 'ITEM만 사용',
    target_message_id BIGINT        NULL COMMENT 'MESSAGE만 사용',
    community_id      BIGINT        NOT NULL COMMENT '신고 맥락의 커뮤니티 (communities.id)',
    reason_code       VARCHAR(30)   NOT NULL,
    detail            VARCHAR(1000) NULL COMMENT 'OTHER일 때 필수',
    status            VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    version           BIGINT        NOT NULL DEFAULT 0,
    target_snapshot   JSON          NOT NULL COMMENT '접수 시 최소 증거 스냅샷. 관리자만 공개',
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_reports_reporter_created (reporter_id, created_at, id),
    KEY idx_reports_community_status_created (community_id, status, created_at, id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES members (id),
    CONSTRAINT fk_reports_target_member FOREIGN KEY (target_member_id) REFERENCES members (id),
    CONSTRAINT fk_reports_target_item FOREIGN KEY (target_item_id) REFERENCES items (id),
    CONSTRAINT fk_reports_target_message FOREIGN KEY (target_message_id) REFERENCES messages (id),
    CONSTRAINT ck_reports_target_type CHECK (target_type IN ('MEMBER', 'ITEM', 'MESSAGE')),
    CONSTRAINT ck_reports_status CHECK (status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT ck_reports_reason_code CHECK (
        reason_code IN ('MONEY_REQUEST', 'ITEM_MISMATCH', 'NO_SHOW', 'ABUSE', 'FRAUD', 'OTHER')
    ),
    CONSTRAINT ck_reports_not_self CHECK (reporter_id <> target_member_id),
    -- MEMBER면 둘 다 NULL, ITEM이면 item만, MESSAGE면 message만 (ERD 4.7)
    CONSTRAINT ck_reports_target_columns CHECK (
        (target_type = 'MEMBER' AND target_item_id IS NULL AND target_message_id IS NULL)
            OR (target_type = 'ITEM' AND target_item_id IS NOT NULL AND target_message_id IS NULL)
            OR (target_type = 'MESSAGE' AND target_message_id IS NOT NULL AND target_item_id IS NULL)
    ),
    CONSTRAINT ck_reports_detail_required CHECK (
        reason_code <> 'OTHER' OR (detail IS NOT NULL AND CHAR_LENGTH(TRIM(detail)) > 0)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '신고 접수와 처리 상태';

CREATE TABLE report_actions (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    report_id     BIGINT         NOT NULL,
    admin_id      BIGINT         NOT NULL COMMENT 'ADMIN 역할 실행자',
    action        VARCHAR(20)    NOT NULL,
    public_reason VARCHAR(500)   NOT NULL COMMENT '대상·신고자에 표시할 사유',
    internal_note VARCHAR(2000)  NULL COMMENT '관리자만 조회',
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    -- 한 신고는 한 번만 처리한다
    UNIQUE KEY uk_report_actions_report (report_id),
    CONSTRAINT fk_report_actions_report FOREIGN KEY (report_id) REFERENCES reports (id),
    CONSTRAINT fk_report_actions_admin FOREIGN KEY (admin_id) REFERENCES members (id),
    CONSTRAINT ck_report_actions_action CHECK (
        action IN ('WARN', 'SUSPEND_7D', 'SUSPEND_30D', 'BAN', 'DISMISS')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '관리자 신고 처리 이력';

CREATE TABLE sanctions (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    member_id        BIGINT       NOT NULL COMMENT '제재 대상',
    report_action_id BIGINT       NOT NULL COMMENT '처리 근거',
    kind             VARCHAR(10)  NOT NULL COMMENT 'SUSPEND / BAN',
    starts_at        DATETIME(6)  NOT NULL COMMENT '처리 시각',
    ends_at          DATETIME(6)  NULL COMMENT 'BAN은 NULL',
    revoked_at       DATETIME(6)  NULL COMMENT '해제 시각',
    revoked_by       BIGINT       NULL COMMENT '해제 관리자',
    revoke_reason    VARCHAR(500) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sanctions_report_action (report_action_id),
    KEY idx_sanctions_member_revoked_ends (member_id, revoked_at, ends_at),
    CONSTRAINT fk_sanctions_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_sanctions_report_action FOREIGN KEY (report_action_id) REFERENCES report_actions (id),
    CONSTRAINT fk_sanctions_revoked_by FOREIGN KEY (revoked_by) REFERENCES members (id),
    CONSTRAINT ck_sanctions_kind CHECK (kind IN ('SUSPEND', 'BAN')),
    -- BAN은 종료 없음, SUSPEND는 시작보다 뒤에서 끝남
    CONSTRAINT ck_sanctions_period CHECK (
        (kind = 'BAN' AND ends_at IS NULL) OR (kind = 'SUSPEND' AND ends_at > starts_at)
    ),
    -- 해제했다면 해제 관리자와 사유가 함께 남는다
    CONSTRAINT ck_sanctions_revoke_columns CHECK (
        (revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL)
            OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revoke_reason IS NOT NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '회원 이용 제한과 해제 이력';
