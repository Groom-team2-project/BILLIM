-- 카테고리 10개 기준 데이터 (도메인 및 DB 설계 5-4). 운영 기준 데이터이며 테스트 더미가 아님.
-- 표시명은 프로토타입 등록·검색 화면 목록과 같다.
INSERT INTO categories (code, name, sort_order, active, created_at, updated_at) VALUES
    ('TOOL',    '공구',     0, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('CAMP',    '캠핑',     1, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('TRAVEL',  '여행',     2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('BABY',    '아기용품', 3, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('MUSIC',   '악기',     4, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('SPORT',   '운동',     5, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('KITCHEN', '주방',     6, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('CLEAN',   '청소',     7, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('LIFE',    '생활용품', 8, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    ('OTHER',   '기타',     9, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
