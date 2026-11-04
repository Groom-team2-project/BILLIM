-- 정리 경합 측정용 배경 세션 95,000건(만료 아님). 회차마다 cleanup-bench.sh가 만료 5,000건 추가 → 정리 시점 전체 10만 건. 로컬 전용
-- 선행: fixtures.sql → scripts/seed-load.sql → session.sql
SET SESSION cte_max_recursion_depth = 100000;

INSERT INTO auth_sessions (member_id, token_hash, csrf_token, last_seen_at, expires_at, absolute_expires_at, created_at, updated_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 95000)
SELECT NULL, SHA2(CONCAT('perf-bg-', i), 256), 'perf-csrf', UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6) + INTERVAL 30 DAY, UTC_TIMESTAMP(6) + INTERVAL 30 DAY, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM n;
