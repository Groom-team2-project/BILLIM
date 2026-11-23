-- 빈 DB용 선행 데이터. seed-load.sql이 참조하는 동네·장소·물건 1건씩. 로컬 전용
INSERT INTO communities (name, display_address, center_latitude, center_longitude, verification_radius_m, created_at, updated_at)
VALUES ('측정동', '서울 측정구 측정동', 37.5000000, 127.0000000, 3000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @community = LAST_INSERT_ID();

INSERT INTO community_places (community_id, name, latitude, longitude, created_at, updated_at)
VALUES (@community, '측정 주민센터', 37.5000000, 127.0000000, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @place = LAST_INSERT_ID();

INSERT INTO members (display_name, role, status, version, created_at, updated_at)
VALUES ('측정소유자', 'USER', 'ACTIVE', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
SET @owner = LAST_INSERT_ID();

INSERT INTO items (owner_id, community_id, category_id, place_id, title, available_start_date, available_end_date,
                   visibility, created_at, updated_at)
VALUES (@owner, @community, (SELECT MIN(id) FROM categories), @place, '측정 물건',
        UTC_DATE(), UTC_DATE() + INTERVAL 365 DAY, 'PUBLIC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
