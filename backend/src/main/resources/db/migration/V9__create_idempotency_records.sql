CREATE TABLE idempotency_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    operation_key VARCHAR(150) NOT NULL,
    request_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    http_status INT NOT NULL,
    response_body JSON NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_member_operation_request UNIQUE (member_id, operation_key, request_key),
    CONSTRAINT fk_idempotency_member FOREIGN KEY (member_id) REFERENCES members(id),
    INDEX idx_idempotency_expires (expires_at)
);
