-- 사건·알림 테이블. 도메인 및 DB 설계 4.6 기준.
-- outbox_events는 사건 생산이 공통, 소비(알림)는 E파트.
-- messages.event_id FK는 chat 테이블 담당이 추가한다. (V4 TODO)

CREATE TABLE outbox_events (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    event_key         VARCHAR(150) NOT NULL COMMENT '예: rental:501:version:2:approved',
    event_type        VARCHAR(50)  NOT NULL COMMENT 'RENTAL_* / APPOINTMENT_* / REPORT_RESOLVED',
    aggregate_type    VARCHAR(20)  NOT NULL COMMENT 'RENTAL / APPOINTMENT_SLOT / REPORT',
    aggregate_id      BIGINT       NOT NULL COMMENT 'aggregate_type 기준 논리 참조. 다형 FK 없음',
    aggregate_version BIGINT       NOT NULL COMMENT '사건 생성 시 version',
    payload           JSON         NOT NULL COMMENT '수신 회원·대상·최소 표시 정보. 인증 비밀값·개인 좌표 금지',
    status            VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    attempts          INT          NOT NULL DEFAULT 0,
    available_at      DATETIME(6)  NOT NULL COMMENT '다음 시도',
    lease_until       DATETIME(6)  NULL COMMENT '작업자 장애 시 재획득',
    processed_at      DATETIME(6)  NULL COMMENT '소비 완료',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_events_event_key (event_key),
    KEY idx_outbox_events_status_available (status, available_at, id),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_events_aggregate_type CHECK (
        aggregate_type IN ('RENTAL', 'APPOINTMENT_SLOT', 'REPORT')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '커밋된 도메인 사건';

CREATE TABLE event_deliveries (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    event_id     BIGINT       NOT NULL,
    consumer_key VARCHAR(100) NOT NULL COMMENT 'notification:{memberId} 또는 room:{roomId}',
    delivered_at DATETIME(6)  NOT NULL COMMENT '결과 저장과 같은 트랜잭션의 성공 기록',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_deliveries_event_consumer (event_id, consumer_key),
    CONSTRAINT fk_event_deliveries_event FOREIGN KEY (event_id) REFERENCES outbox_events (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '소비자별 사건 중복 방지';

CREATE TABLE notifications (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_id BIGINT       NOT NULL,
    event_id     BIGINT       NOT NULL,
    type         VARCHAR(50)  NOT NULL COMMENT '사건 유형',
    title        VARCHAR(200) NOT NULL COMMENT '당시 표시 문구',
    target_type  VARCHAR(20)  NOT NULL COMMENT 'RENTAL / REPORT / MEMBER_SELF',
    target_id    BIGINT       NOT NULL COMMENT 'target_type 기준 논리 참조',
    read_at      DATETIME(6)  NULL COMMENT '미열람 NULL',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_event_recipient (event_id, recipient_id),
    KEY idx_notifications_recipient_read (recipient_id, read_at, id),
    KEY idx_notifications_recipient_id (recipient_id, id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES members (id),
    CONSTRAINT fk_notifications_event FOREIGN KEY (event_id) REFERENCES outbox_events (id),
    CONSTRAINT ck_notifications_target_type CHECK (
        target_type IN ('RENTAL', 'REPORT', 'MEMBER_SELF')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '회원별 앱 내 알림';
