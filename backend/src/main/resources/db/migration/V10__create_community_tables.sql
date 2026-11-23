-- 커뮤니티·소속·공용 장소 테이블. 도메인 및 DB 설계 4.2 기준.
-- 개인 위치·정확도·이동 기록은 저장하지 않는다. 좌표 컬럼은 공개 장소 기준 정보다. (ERD 2장)
-- communities 기준 데이터는 운영 seed로 등록한다. 생성·경계 편집 관리자 UI는 범위 밖.
-- items·rentals의 community_id·place_id FK는 해당 테이블 담당이 추가한다. (V1·V6 보류 FK)

CREATE TABLE communities (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(100)   NOT NULL COMMENT '동네/단지 이름',
    display_address       VARCHAR(255)   NOT NULL COMMENT '공개 커뮤니티 주소. 개인 집 주소 아님',
    center_latitude       DECIMAL(10, 7) NOT NULL,
    center_longitude      DECIMAL(10, 7) NOT NULL,
    verification_radius_m INT            NOT NULL COMMENT '양수 인증 반경',
    status                VARCHAR(10)    NOT NULL DEFAULT 'ACTIVE',
    created_at            DATETIME(6)    NOT NULL,
    updated_at            DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_communities_status_name (status, name, id),
    CONSTRAINT ck_communities_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_communities_latitude CHECK (center_latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_communities_longitude CHECK (center_longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_communities_radius CHECK (verification_radius_m > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '생활권 커뮤니티 기준 정보';

CREATE TABLE community_memberships (
    id                        BIGINT      NOT NULL AUTO_INCREMENT,
    member_id                 BIGINT      NOT NULL,
    community_id              BIGINT      NOT NULL,
    status                    VARCHAR(10) NOT NULL COMMENT 'ACTIVE / LEFT',
    verified_at               DATETIME(6) NOT NULL COMMENT '마지막 성공 인증 시각',
    verification_expires_at   DATETIME(6) NOT NULL COMMENT 'verified_at + 30일',
    left_at                   DATETIME(6) NULL COMMENT '이전 소속에서 이탈한 시각',
    created_at                DATETIME(6) NOT NULL,
    updated_at                DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    -- 인증 재시도는 같은 행을 갱신한다
    UNIQUE KEY uk_community_memberships_member_community (member_id, community_id),
    KEY idx_community_memberships_community_status_expires (community_id, status, verification_expires_at),
    CONSTRAINT fk_community_memberships_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_community_memberships_community FOREIGN KEY (community_id) REFERENCES communities (id),
    CONSTRAINT ck_community_memberships_status CHECK (status IN ('ACTIVE', 'LEFT')),
    CONSTRAINT ck_community_memberships_expires CHECK (verification_expires_at > verified_at),
    -- LEFT면 이탈 시각이 남고, ACTIVE면 없다
    CONSTRAINT ck_community_memberships_left_at CHECK (
        (status = 'LEFT' AND left_at IS NOT NULL) OR (status = 'ACTIVE' AND left_at IS NULL)
    )
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '회원별 소속·인증 결과';

CREATE TABLE community_places (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    community_id BIGINT         NOT NULL,
    name         VARCHAR(100)   NOT NULL COMMENT '공용 장소 이름',
    latitude     DECIMAL(10, 7) NOT NULL COMMENT '공개 장소 좌표',
    longitude    DECIMAL(10, 7) NOT NULL COMMENT '공개 장소 좌표',
    guide        VARCHAR(300)   NULL COMMENT '만남 안내',
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_community_places_community_active (community_id, active, id),
    CONSTRAINT fk_community_places_community FOREIGN KEY (community_id) REFERENCES communities (id),
    CONSTRAINT ck_community_places_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_community_places_longitude CHECK (longitude BETWEEN -180 AND 180)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '물건과 약속이 공유하는 공용 장소';
