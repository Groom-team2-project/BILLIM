-- B파트(물건·카테고리·사진) 테이블. 도메인 및 DB 설계 4.3 기준.
-- TODO: members·communities·community_places는 다른 담당 마이그레이션이 만든 뒤 FK 추가 마이그레이션 필요.
--       (items.owner_id/community_id/place_id, media_files.uploader_id) 해당 테이블이 생기면 제거.
-- 번호는 develop의 최신 번호 확인 후 조정.

CREATE TABLE categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20) NOT NULL,
    name       VARCHAR(30) NOT NULL,
    sort_order INT         NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_code (code),
    CONSTRAINT ck_categories_code CHECK (code IN ('TOOL', 'CAMP', 'TRAVEL', 'BABY', 'MUSIC', 'SPORT', 'KITCHEN', 'CLEAN', 'LIFE', 'OTHER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE media_files (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    uploader_id BIGINT       NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(30)  NOT NULL,
    byte_size   BIGINT       NOT NULL,
    width       INT          NOT NULL,
    height      INT          NOT NULL,
    status      VARCHAR(10)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    deleted_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_media_files_storage_key (storage_key),
    KEY idx_media_files_status_expires (status, expires_at),
    KEY idx_media_files_uploader (uploader_id, id),
    CONSTRAINT ck_media_files_status CHECK (status IN ('TEMP', 'ATTACHED', 'DELETED')),
    CONSTRAINT ck_media_files_size CHECK (byte_size BETWEEN 1 AND 10485760),
    CONSTRAINT ck_media_files_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_media_files_dimension CHECK (width > 0 AND height > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE items (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    owner_id             BIGINT        NOT NULL,
    community_id         BIGINT        NOT NULL,
    category_id          BIGINT        NOT NULL,
    place_id             BIGINT        NOT NULL,
    title                VARCHAR(100)  NOT NULL,
    description          VARCHAR(3000) NULL,
    available_start_date DATE          NOT NULL,
    available_end_date   DATE          NOT NULL,
    visibility           VARCHAR(10)   NOT NULL,
    deleted_at           DATETIME(6)   NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_items_community_visibility_created (community_id, visibility, created_at, id),
    KEY idx_items_community_visibility_category_created (community_id, visibility, category_id, created_at, id),
    KEY idx_items_owner_visibility (owner_id, visibility, id),
    CONSTRAINT fk_items_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_items_dates CHECK (available_start_date <= available_end_date),
    CONSTRAINT ck_items_visibility CHECK (visibility IN ('PUBLIC', 'HIDDEN', 'DELETED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE item_images (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    item_id       BIGINT      NOT NULL,
    media_file_id BIGINT      NOT NULL,
    sort_order    INT         NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_images_item_sort (item_id, sort_order),
    UNIQUE KEY uk_item_images_media (media_file_id),
    CONSTRAINT fk_item_images_item FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_item_images_media FOREIGN KEY (media_file_id) REFERENCES media_files (id),
    CONSTRAINT ck_item_images_sort CHECK (sort_order BETWEEN 0 AND 4)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
