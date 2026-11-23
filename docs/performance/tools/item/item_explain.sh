#!/usr/bin/env bash
# B파트 검색 관련 주요 쿼리의 EXPLAIN ANALYZE를 격리 스택에서 실행해 화면에 출력한다.
# 저장 예: bash docs/performance/tools/item/item_explain.sh > docs/performance/raw/item-explain-$(git rev-parse --short HEAD).txt
# 전제: item_seed.py 실행 완료 (B파트 동네 910100)
set -uo pipefail
cd "$(dirname "$0")/../../../.."
C=910100
run() {
  echo "### $1"
  echo "EXPLAIN ANALYZE $2" | docker compose -f docs/performance/tools/item/compose.yaml exec -T mysql sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -r billim_perf'
  echo
}
echo "커밋 $(git rev-parse --short HEAD) · $(date '+%Y-%m-%d %H:%M')"
echo
run "Q1 (개선 전 검색) 차단 판정용 소유자 전체" "SELECT DISTINCT owner_id FROM items WHERE community_id=$C AND visibility='PUBLIC';"
run "Q1' (개선 후 검색) 차단 관계 회원만" "SELECT CASE WHEN blocker_id=1999999 THEN blocked_id ELSE blocker_id END FROM blocks WHERE blocker_id=1999999 OR blocked_id=1999999;"
run "Q2 전체 건수" "SELECT COUNT(*) FROM items WHERE community_id=$C AND visibility='PUBLIC';"
run "Q3 최신순 20건" "SELECT id FROM items WHERE community_id=$C AND visibility='PUBLIC' ORDER BY created_at DESC, id DESC LIMIT 20;"
run "Q4 깊은 페이지 OFFSET 20000" "SELECT id FROM items WHERE community_id=$C AND visibility='PUBLIC' ORDER BY created_at DESC, id DESC LIMIT 20 OFFSET 20000;"
run "Q5 키워드 건수" "SELECT COUNT(*) FROM items WHERE community_id=$C AND visibility='PUBLIC' AND title LIKE '%드릴%';"
run "Q6 기간 건수 (NOT EXISTS 2개)" "SELECT COUNT(*) FROM items i WHERE i.community_id=$C AND i.visibility='PUBLIC' AND i.available_start_date<=CURDATE()+INTERVAL 1 DAY AND i.available_end_date>=CURDATE()+INTERVAL 7 DAY AND NOT EXISTS (SELECT 1 FROM rentals r WHERE r.item_id=i.id AND r.status IN ('APPROVED','ACTIVE','RETURNED') AND r.start_date<=CURDATE()+INTERVAL 7 DAY AND r.end_date>=CURDATE()+INTERVAL 1 DAY) AND NOT EXISTS (SELECT 1 FROM rentals o WHERE o.item_id=i.id AND o.status='ACTIVE' AND o.due_at<=UTC_TIMESTAMP());"
run "Q7 지도 장소별 집계" "SELECT place_id, COUNT(*) FROM items WHERE community_id=$C AND visibility='PUBLIC' GROUP BY place_id;"
run "Q8 가까운 순 20건" "SELECT id FROM items WHERE community_id=$C AND visibility='PUBLIC' ORDER BY CASE place_id WHEN 910100 THEN 10 WHEN 910101 THEN 20 WHEN 910102 THEN 30 ELSE 2147483647 END ASC, id DESC LIMIT 20;"
