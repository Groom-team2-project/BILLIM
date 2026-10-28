-- E_069 통계 측정용 시드. 동네 20개 · 물건 400개 · 대여 20,000건
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO members (display_name, status, role, created_at, updated_at)
SELECT CONCAT('측정회원', n), 'ACTIVE', 'USER', NOW(6), NOW(6)
FROM (WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM s WHERE n < 50) SELECT n FROM s) t;

INSERT INTO communities (name, display_address, center_latitude, center_longitude,
                         verification_radius_m, status, created_at, updated_at)
SELECT CONCAT('측정동네', n), CONCAT('서울특별시 강남구 측정동네', n),
       37.4953, 127.0330, 3000, 'ACTIVE', NOW(6), NOW(6)
FROM (WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM s WHERE n < 20) SELECT n FROM s) t;

SET @m := (SELECT MIN(id) FROM members WHERE display_name LIKE '측정회원%');
SET @c := (SELECT MIN(id) FROM communities WHERE name LIKE '측정동네%');

INSERT INTO items (owner_id, community_id, category_id, place_id, title,
                   available_start_date, available_end_date, visibility, version, created_at, updated_at)
SELECT @m + (n % 50), @c + (n % 20), 1 + (n % 10), 1, CONCAT('측정물건', n),
       '2026-01-01', '2026-12-31',
       CASE WHEN n % 20 = 0 THEN 'HIDDEN' WHEN n % 50 = 0 THEN 'DELETED' ELSE 'PUBLIC' END,
       0, TIMESTAMPADD(HOUR, -n, '2026-10-28 00:00:00')
     , TIMESTAMPADD(HOUR, -n, '2026-10-28 00:00:00')
FROM (WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM s WHERE n < 400) SELECT n FROM s) t;

INSERT INTO rentals (item_id, owner_id, borrower_id, community_id, start_date, end_date, status,
                     request_expires_at, due_at, item_title_snapshot, place_name_snapshot,
                     place_id, version, created_at, updated_at)
SELECT i.id, i.owner_id, @m + ((t.n + 7) % 50), i.community_id,
       '2026-10-10', '2026-10-11',
       ELT(1 + (t.n % 5), 'REQUESTED', 'APPROVED', 'ACTIVE', 'RETURNED', 'REJECTED'),
       NOW(6), NOW(6), i.title, '주민센터', 1, 0,
       TIMESTAMPADD(MINUTE, -t.n * 20, '2026-10-28 00:00:00'),
       TIMESTAMPADD(MINUTE, -t.n * 20, '2026-10-28 00:00:00')
FROM (WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM s WHERE n < 20000) SELECT n FROM s) t
         JOIN items i ON i.id = (SELECT MIN(id) FROM items WHERE title LIKE '측정물건%') + (t.n % 400);

ANALYZE TABLE rentals, items;
