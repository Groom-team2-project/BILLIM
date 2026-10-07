-- 채팅 소유 테이블. 아직 없는 회원·대여·약속·사건 테이블의 FK는 이후 추가한다.

CREATE TABLE chat_rooms (
                            id              BIGINT      NOT NULL AUTO_INCREMENT,
                            item_id         BIGINT      NOT NULL,
                            owner_id        BIGINT      NOT NULL,
                            requester_id    BIGINT      NOT NULL,
                            next_sequence   BIGINT      NOT NULL DEFAULT 1,
                            last_message_at DATETIME(6) NULL,
                            created_at      DATETIME(6) NOT NULL,
                            updated_at      DATETIME(6) NOT NULL,
                            PRIMARY KEY (id),
                            UNIQUE KEY uk_chat_rooms_item_requester (item_id, requester_id),
                            KEY idx_chat_rooms_owner_last_message (owner_id, last_message_at, id),
                            KEY idx_chat_rooms_requester_last_message (requester_id, last_message_at, id),
                            CONSTRAINT fk_chat_rooms_item
                                FOREIGN KEY (item_id) REFERENCES items (id),
                            CONSTRAINT ck_chat_rooms_distinct_members
                                CHECK (owner_id <> requester_id),
                            CONSTRAINT ck_chat_rooms_next_sequence
                                CHECK (next_sequence >= 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE chat_participants (
                                   id                 BIGINT      NOT NULL AUTO_INCREMENT,
                                   room_id            BIGINT      NOT NULL,
                                   member_id          BIGINT      NOT NULL,
                                   last_read_sequence BIGINT      NOT NULL DEFAULT 0,
                                   created_at         DATETIME(6) NOT NULL,
                                   updated_at         DATETIME(6) NOT NULL,
                                   PRIMARY KEY (id),
                                   UNIQUE KEY uk_chat_participants_room_member (room_id, member_id),
                                   KEY idx_chat_participants_member_room (member_id, room_id),
                                   CONSTRAINT fk_chat_participants_room
                                       FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
                                   CONSTRAINT ck_chat_participants_read_sequence
                                       CHECK (last_read_sequence >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE messages (
                          id                      BIGINT        NOT NULL AUTO_INCREMENT,
                          room_id                 BIGINT        NOT NULL,
                          sequence                BIGINT        NOT NULL,
                          sender_id               BIGINT        NULL,
                          type                    VARCHAR(10)   NOT NULL,
                          body                    VARCHAR(2000) NULL,
                          rental_id               BIGINT        NULL,
                          appointment_proposal_id BIGINT        NULL,
                          event_id                BIGINT        NULL,
                          client_message_id       CHAR(36)      NULL,
                          system_payload          JSON          NULL,
                          created_at              DATETIME(6)   NOT NULL,
                          updated_at              DATETIME(6)   NOT NULL,
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_messages_room_sequence (room_id, sequence),
                          UNIQUE KEY uk_messages_client_key
                              (room_id, sender_id, client_message_id),
                          UNIQUE KEY uk_messages_room_event (room_id, event_id),
                          CONSTRAINT fk_messages_room
                              FOREIGN KEY (room_id) REFERENCES chat_rooms (id),
                          CONSTRAINT ck_messages_sequence CHECK (sequence >= 1),
                          CONSTRAINT ck_messages_type CHECK (type IN ('TEXT', 'SYSTEM')),
                          CONSTRAINT ck_messages_text_fields CHECK (
                              type <> 'TEXT'
                                  OR (
                                  sender_id IS NOT NULL
                                      AND client_message_id IS NOT NULL
                                      AND body IS NOT NULL
                                      AND CHAR_LENGTH(body) BETWEEN 1 AND 2000
                                  )
                              )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
