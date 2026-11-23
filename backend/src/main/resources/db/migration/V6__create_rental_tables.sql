-- 대여·상태 이력·약속 테이블. 도메인 및 DB 설계 4.4 기준.
-- communities·community_places 생성 후 동네·장소 FK 추가 필요.

CREATE TABLE rentals (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    item_id              BIGINT       NOT NULL,
    owner_id             BIGINT       NOT NULL,
    borrower_id          BIGINT       NOT NULL,
    community_id         BIGINT       NOT NULL,
    chat_room_id         BIGINT       NULL,
    start_date           DATE         NOT NULL,
    end_date             DATE         NOT NULL,
    status               VARCHAR(15)  NOT NULL,
    request_expires_at   DATETIME(6)  NOT NULL,
    due_at               DATETIME(6)  NOT NULL,
    item_title_snapshot  VARCHAR(100) NOT NULL,
    place_name_snapshot  VARCHAR(100) NOT NULL,
    place_id             BIGINT       NOT NULL,
    reason               VARCHAR(500) NULL,
    approved_at          DATETIME(6)  NULL,
    handed_over_at       DATETIME(6)  NULL,
    returned_at          DATETIME(6)  NULL,
    return_promise_at    DATETIME(6)  NULL,
    return_promise_kept  BOOLEAN      NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_rentals_item_status_dates (item_id, status, start_date, end_date),
    KEY idx_rentals_borrower_status_created (borrower_id, status, created_at, id),
    KEY idx_rentals_owner_status_created (owner_id, status, created_at, id),
    KEY idx_rentals_status_expires (status, request_expires_at),
    KEY idx_rentals_chat_room (chat_room_id, id),
    CONSTRAINT fk_rentals_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_rentals_owner FOREIGN KEY (owner_id) REFERENCES members (id),
    CONSTRAINT fk_rentals_borrower FOREIGN KEY (borrower_id) REFERENCES members (id),
    CONSTRAINT fk_rentals_chat_room FOREIGN KEY (chat_room_id) REFERENCES chat_rooms (id),
    CONSTRAINT ck_rentals_dates CHECK (start_date <= end_date),
    CONSTRAINT ck_rentals_distinct_members CHECK (owner_id <> borrower_id),
    CONSTRAINT ck_rentals_status CHECK (
        status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELED', 'ACTIVE', 'RETURNED', 'EXPIRED')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE rental_status_histories (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id      BIGINT       NOT NULL,
    actor_id       BIGINT       NULL,
    from_status    VARCHAR(15)  NULL,
    to_status      VARCHAR(15)  NOT NULL,
    reason         VARCHAR(500) NULL,
    rental_version BIGINT       NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rental_history_version (rental_id, rental_version),
    CONSTRAINT fk_rental_history_rental FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_rental_history_actor FOREIGN KEY (actor_id) REFERENCES members (id),
    CONSTRAINT ck_rental_history_from_status CHECK (
        from_status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELED', 'ACTIVE', 'RETURNED', 'EXPIRED')
    ),
    CONSTRAINT ck_rental_history_to_status CHECK (
        to_status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CANCELED', 'ACTIVE', 'RETURNED', 'EXPIRED')
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE appointment_slots (
    id                    BIGINT      NOT NULL AUTO_INCREMENT,
    rental_id             BIGINT      NOT NULL,
    kind                  VARCHAR(10) NOT NULL,
    confirmed_proposal_id BIGINT      NULL,
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            DATETIME(6) NOT NULL,
    updated_at            DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointment_slot_rental_kind (rental_id, kind),
    CONSTRAINT fk_appointment_slot_rental FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT ck_appointment_slot_kind CHECK (kind IN ('PICKUP', 'RETURN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE appointment_proposals (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    slot_id             BIGINT       NOT NULL,
    proposer_id         BIGINT       NOT NULL,
    place_id            BIGINT       NOT NULL,
    scheduled_at        DATETIME(6)  NOT NULL,
    place_name_snapshot VARCHAR(100) NOT NULL,
    status              VARCHAR(15)  NOT NULL,
    accepted_by         BIGINT       NULL,
    accepted_at         DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_appointment_proposal_slot_status (slot_id, status, id),
    CONSTRAINT fk_appointment_proposal_slot FOREIGN KEY (slot_id) REFERENCES appointment_slots (id),
    CONSTRAINT fk_appointment_proposal_proposer FOREIGN KEY (proposer_id) REFERENCES members (id),
    CONSTRAINT fk_appointment_proposal_acceptor FOREIGN KEY (accepted_by) REFERENCES members (id),
    CONSTRAINT ck_appointment_proposal_status CHECK (
        status IN ('PROPOSED', 'CONFIRMED', 'SUPERSEDED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_appointment_proposal_distinct_members CHECK (accepted_by <> proposer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 순환 참조는 두 테이블 생성 후 연결. 동일 슬롯·CONFIRMED 여부는 서비스에서 검사.
ALTER TABLE appointment_slots
    ADD CONSTRAINT fk_appointment_slot_confirmed_proposal
        FOREIGN KEY (confirmed_proposal_id) REFERENCES appointment_proposals (id);
