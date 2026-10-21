SELECT '=== 1. 주변 동네 좌표 사각형 (E_071) ===' AS q;
EXPLAIN ANALYZE
SELECT c.* FROM communities c
WHERE c.status='ACTIVE'
  AND c.center_latitude  BETWEEN 37.3201 AND 37.6799
  AND c.center_longitude BETWEEN 126.7735 AND 127.2265;

SELECT '=== 1b. 선필터 전 방식: 활성 동네 전체 조회 ===' AS q;
EXPLAIN ANALYZE SELECT c.* FROM communities c WHERE c.status='ACTIVE';

SELECT '=== 2. 동네 검색 LIKE (E_009) ===' AS q;
EXPLAIN ANALYZE
SELECT c.* FROM communities c
WHERE c.status='ACTIVE'
  AND (c.name LIKE '%역삼%' OR c.display_address LIKE '%역삼%')
ORDER BY c.name ASC, c.id ASC LIMIT 20;

SELECT '=== 3. 유효 소속 수 집계 (동네 목록마다 호출) ===' AS q;
EXPLAIN ANALYZE
SELECT cm.community_id, COUNT(*) FROM community_memberships cm
WHERE cm.community_id IN (1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20)
  AND cm.status='ACTIVE' AND cm.verification_expires_at > UTC_TIMESTAMP(6)
GROUP BY cm.community_id;

SELECT '=== 4. 관리자 신고 목록 깊은 페이지 (offset 10000) ===' AS q;
EXPLAIN ANALYZE
SELECT r.* FROM reports r ORDER BY r.created_at DESC, r.id DESC LIMIT 20 OFFSET 10000;

SELECT '=== 5. 관리자 신고 목록 첫 페이지 ===' AS q;
EXPLAIN ANALYZE
SELECT r.* FROM reports r ORDER BY r.created_at DESC, r.id DESC LIMIT 20 OFFSET 0;

SELECT '=== 인덱스 현황 ===' AS q;
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA='billim' AND TABLE_NAME IN ('communities','community_memberships','reports','notices')
GROUP BY TABLE_NAME, INDEX_NAME ORDER BY TABLE_NAME, INDEX_NAME;

SELECT '=== 6. Page 전체 건수 세기 (목록마다 함께 나가는 COUNT) ===' AS q;
EXPLAIN ANALYZE SELECT COUNT(*) FROM reports;
EXPLAIN ANALYZE SELECT COUNT(*) FROM communities WHERE status='ACTIVE' AND (name LIKE '%역삼%' OR display_address LIKE '%역삼%');
