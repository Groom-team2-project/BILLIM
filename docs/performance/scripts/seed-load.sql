-- 성능 측정용 데이터 적재. 로컬 전용 — 운영 사용 금지
-- 대상: A_005(/members/me)·A_007(/members/{id})가 조회하는 members·community_memberships·rentals·blocks
SET SESSION cte_max_recursion_depth = 100000;

SET @community = (SELECT id FROM communities ORDER BY id LIMIT 1);
SET @item      = (SELECT id FROM items ORDER BY id LIMIT 1);
SET @place     = (SELECT id FROM community_places ORDER BY id LIMIT 1);

-- 회원 1,000명
INSERT INTO members (display_name, role, status, version, created_at, updated_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 1000)
SELECT CONCAT('부하회원', i), 'USER', 'ACTIVE', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) FROM n;

-- 같은 동네 소속 — 전원 유효 인증
INSERT INTO community_memberships (member_id, community_id, status, verified_at,
                                   verification_expires_at, created_at, updated_at)
SELECT m.id, @community, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6) + INTERVAL 30 DAY,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM members m
WHERE m.display_name LIKE '부하회원%'
  AND NOT EXISTS (SELECT 1 FROM community_memberships cm WHERE cm.member_id = m.id);

-- 거래 20,000건 — hasTraded 비용 노출용으로 당사자 조합 분산
INSERT INTO rentals (item_id, owner_id, borrower_id, community_id, start_date, end_date, status,
                     request_expires_at, due_at, item_title_snapshot, place_name_snapshot, place_id,
                     created_at, updated_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20000),
ids AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM members WHERE display_name LIKE '부하회원%')
SELECT @item,
       o.id, b.id, @community,
       CURRENT_DATE, CURRENT_DATE + INTERVAL 2 DAY, 'RETURNED',
       UTC_TIMESTAMP(6) + INTERVAL 1 DAY, UTC_TIMESTAMP(6) + INTERVAL 2 DAY,
       '부하 측정 물건', '관리동 무인택배함 앞', @place,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM n
JOIN ids o ON o.rn = (n.i % 1000) + 1
JOIN ids b ON b.rn = ((n.i * 7) % 1000) + 1
WHERE o.id <> b.id;

-- 차단 500건
INSERT INTO blocks (blocker_id, blocked_id, created_at, updated_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 500),
ids AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM members WHERE display_name LIKE '부하회원%')
SELECT a.id, c.id, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM n
JOIN ids a ON a.rn = (n.i % 1000) + 1
JOIN ids c ON c.rn = ((n.i * 13) % 1000) + 1
WHERE a.id <> c.id;

SELECT (SELECT COUNT(*) FROM members) members,
       (SELECT COUNT(*) FROM community_memberships) memberships,
       (SELECT COUNT(*) FROM rentals) rentals,
       (SELECT COUNT(*) FROM blocks) blocks;

-- 되돌리기:
--   DELETE FROM blocks;
--   DELETE FROM rentals;
--   DELETE FROM community_memberships WHERE member_id IN (SELECT id FROM members WHERE display_name LIKE '부하회원%');
--   DELETE FROM members WHERE display_name LIKE '부하회원%';
