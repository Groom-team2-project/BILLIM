#!/usr/bin/env bash
# 정리 경합 측정 — 묶음 크기별로 조회 부하 중 만료 세션 5,000건 정리 시 응답시간·처리량 관찰. 로컬 전용
# 구성마다: 앱 기동 → 워밍업 → REPEAT회(k6 시작 → 만료 투입 → 정리 → k6 종료) → 앱 종료
#
# 회차 일정(성능 측정 5절과 동일): 짝수 분 :40 k6 시작 → +25s 만료 투입 → +80s(짝수 분 :00) 정리 = 유지 구간 중반
# 정리는 2분마다 — ShedLock lockAtLeastFor 1m 때문에 매분 cron이면 직전 실행의 잠금에 걸려 한 번씩 건너뜀
#
# 전제: billim-perf-mysql(13306)에 fixtures.sql → scripts/seed-load.sql → session.sql → cleanup-seed.sql 적재, bootJar 빌드
# 사용: CONFIGS="10000:0 500:50" REPEAT=3 bash docs/performance/tools/auth/cleanup-bench.sh
#   구성 = 묶음 크기:묶음 간 대기(ms). 10000은 5,000건을 한 트랜잭션으로 삭제 — 묶음 분할 전과 같은 동작
set -uo pipefail
cd "$(dirname "$0")/../../../.."

TOOL="docs/performance/tools/auth"
JS="docs/performance/scripts/member-read.js"
JAR="backend/build/libs/billim-0.0.1-SNAPSHOT.jar"
CONFIGS=${CONFIGS:-"10000:0 500:50"}
REPEAT=${REPEAT:-3}
EXPIRED=${EXPIRED:-5000}
OUT="backend/build/perf/cleanup-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

sql() { docker exec -i billim-perf-mysql mysql -uroot -prootpw -N billim 2>/dev/null; }

# 보존 기간 7일보다 오래된 절대 만료 — 다음 정리 대상
inject_expired() {   # $1 회차 식별자
  sql <<SQL
SET SESSION cte_max_recursion_depth = 100000;
INSERT INTO auth_sessions (member_id, token_hash, csrf_token, last_seen_at, expires_at, absolute_expires_at, created_at, updated_at)
WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < $EXPIRED)
SELECT NULL, SHA2(CONCAT('perf-exp-$1-', i), 256), 'perf-csrf', UTC_TIMESTAMP(6) - INTERVAL 9 DAY,
       UTC_TIMESTAMP(6) - INTERVAL 8 DAY, UTC_TIMESTAMP(6) - INTERVAL 8 DAY, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM n;
SQL
}

expired_left() {
  echo "SELECT COUNT(*) FROM auth_sessions WHERE absolute_expires_at < UTC_TIMESTAMP(6) - INTERVAL 7 DAY" | sql
}

wait_even_minute_40() {
  while :; do
    m=$(date +%-M); s=$(date +%-S)
    [ $((m % 2)) -eq 0 ] && [ "$s" -eq 40 ] && return
    sleep 0.2
  done
}

start_app() {   # $1 묶음 크기, $2 대기, $3 로그
  SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13306/billim' SPRING_DATASOURCE_USERNAME=billim \
  SPRING_DATASOURCE_PASSWORD=perfpw KAKAO_CLIENT_ID=x KAKAO_CLIENT_SECRET=x \
  CLEANUP_CRON="0 */2 * * * *" CLEANUP_CHUNK_SIZE="$1" CLEANUP_CHUNK_PAUSE_MS="$2" \
    java -Xmx768m -jar "$JAR" --spring.profiles.active=local --spring.docker.compose.enabled=false \
    --server.port=18080 --management.server.port=19090 --billim.auth.rate-limit-enabled=false \
    --logging.level.org.hibernate.SQL=info --logging.level.org.hibernate.orm.jdbc.bind=info \
    > "$3" 2>&1 &
  APP=$!
  for _ in $(seq 1 90); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:18080/api/v1/auth/csrf)" = 200 ] && return
    sleep 2
  done
  echo "✗ 앱 3분 내 미기동 — 로그: $3" >&2
  exit 1
}

stop_app() {
  [ -n "${APP:-}" ] && kill "$APP" 2>/dev/null && wait "$APP" 2>/dev/null
  APP=""
}

trap 'stop_app' EXIT

K="k6 run --quiet -e BASE=http://localhost:18080/api/v1 -e SESSION=perf-session-0001 -e TARGET=4"

for cfg in $CONFIGS; do
  chunk=${cfg%:*}; pause=${cfg#*:}
  echo "▶ 묶음 $chunk · 대기 ${pause}ms"
  start_app "$chunk" "$pause" "$OUT/app-$chunk.log"

  if ! $K -e STEADY=20s "$JS" > "$OUT/$chunk-warmup.log" 2>&1; then
    echo "✗ 워밍업 실패 — $OUT/$chunk-warmup.log" >&2
    exit 1
  fi
  for r in $(seq 1 "$REPEAT"); do
    wait_even_minute_40
    start=$(date +%s)
    $K -e STEADY=110s --out csv="$OUT/$chunk-run$r.csv" "$JS" > "$OUT/$chunk-run$r.log" 2>&1 &
    K6=$!
    sleep 25
    inject_expired "$chunk-$r"
    wait $K6
    left=$(expired_left)
    # 정리가 유지 구간 안에 돌지 않은 회차는 측정값에서 제외 — 중단
    if [ "$left" != 0 ]; then
      echo "✗ $chunk ${r}회차 정리 미실행(남은 만료 ${left}건) — $OUT/app-$chunk.log" >&2
      exit 1
    fi
    echo "$chunk,$r,$start" >> "$OUT/runs.csv"
    echo "  ${r}회차 완료"
  done
  stop_app
done
echo "결과: $OUT"
python3 "$TOOL/cleanup_report.py" "$OUT"
