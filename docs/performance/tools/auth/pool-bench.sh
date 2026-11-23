#!/usr/bin/env bash
# 인스턴스 수 × 커넥션 풀 크기별 DB 연결 수·MySQL 메모리·처리량 측정. 로컬 전용
# 구성마다: 앱 기동 → 유휴 표집 → 워밍업 → REPEAT회 부하(표집 병행) → 앱 종료
#
# 전제: billim-perf-mysql 컨테이너(13306)에 fixtures.sql → scripts/seed-load.sql → session.sql 적재, bootJar 빌드
# 사용: CONFIGS="1x30 2x15 2x30" REPEAT=3 bash docs/performance/tools/auth/pool-bench.sh
set -uo pipefail
cd "$(dirname "$0")/../../../.."

TOOL="docs/performance/tools/auth"
JAR="backend/build/libs/billim-0.0.1-SNAPSHOT.jar"
CONFIGS=${CONFIGS:-"1x30 2x15 2x30"}
REPEAT=${REPEAT:-3}
VUS=${VUS:-80}
STEADY=${STEADY:-60s}
OUT="backend/build/perf/pool-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

sql() { docker exec billim-perf-mysql mysql -uroot -prootpw -N -e "$1" 2>/dev/null; }

# 2초 간격 표집: 시각, 연결 수, performance_schema 집계 메모리(바이트), 컨테이너 메모리
sample() {
  while :; do
    conn=$(sql "SELECT VARIABLE_VALUE FROM performance_schema.global_status WHERE VARIABLE_NAME='Threads_connected'")
    mem=$(sql "SELECT total_allocated FROM sys.x\$memory_global_total")
    cmem=$(docker stats --no-stream --format '{{.MemUsage}}' billim-perf-mysql | cut -d/ -f1 | tr -d ' ')
    echo "$(date +%s),$conn,$mem,$cmem"
    sleep 2
  done
}

start_instances() {   # $1 대수, $2 풀 크기
  local n=$1 pool=$2
  for i in $(seq 0 $((n - 1))); do
    SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13306/billim' SPRING_DATASOURCE_USERNAME=billim \
    SPRING_DATASOURCE_PASSWORD=perfpw KAKAO_CLIENT_ID=x KAKAO_CLIENT_SECRET=x \
      java -Xmx768m -jar "$JAR" --spring.profiles.active=local --spring.docker.compose.enabled=false \
      --server.port=$((18080 + i)) --management.server.port=$((19090 + i)) \
      --spring.datasource.hikari.maximum-pool-size="$pool" --billim.auth.rate-limit-enabled=false \
      --logging.level.org.hibernate.SQL=info --logging.level.org.hibernate.orm.jdbc.bind=info \
      > "$OUT/app-${n}x${pool}-$i.log" 2>&1 &
    echo $! >> "$OUT/pids"
  done
  for i in $(seq 0 $((n - 1))); do
    local ready=""
    for _ in $(seq 1 90); do
      if [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:$((18080 + i))/api/v1/auth/csrf)" = 200 ]; then
        ready=1; break
      fi
      sleep 2
    done
    # 미기동 인스턴스에 부하를 보내면 오류 응답이 측정값으로 남음 — 중단
    if [ -z "$ready" ]; then
      echo "✗ 인스턴스 $((18080 + i)) 3분 내 미기동 — 로그: $OUT/app-${n}x${pool}-$i.log" >&2
      exit 1
    fi
  done
}

stop_instances() {
  [ -f "$OUT/pids" ] && xargs kill < "$OUT/pids" 2>/dev/null
  rm -f "$OUT/pids"
  for _ in $(seq 1 30); do pgrep -f "$JAR" >/dev/null || break; sleep 1; done
}

trap 'stop_instances; kill $SAMPLER 2>/dev/null' EXIT

for cfg in $CONFIGS; do
  n=${cfg%x*}; pool=${cfg#*x}
  bases=$(for i in $(seq 0 $((n - 1))); do printf 'http://localhost:%d,' $((18080 + i)); done | sed 's/,$//')
  echo "▶ $cfg ($bases)"
  stop_instances
  start_instances "$n" "$pool"

  # 유휴: Hikari가 최소 유휴(=최대)까지 연결을 채울 시간
  sleep 15
  sample > "$OUT/$cfg-idle.csv" & SAMPLER=$!; sleep 10; kill $SAMPLER

  if ! k6 run --quiet -e BASES="$bases" -e VUS="$VUS" -e STEADY=20s "$TOOL/member-read-multi.js" > "$OUT/$cfg-warmup.log" 2>&1; then
    echo "✗ $cfg 워밍업 실패 — $OUT/$cfg-warmup.log" >&2
    exit 1
  fi
  for r in $(seq 1 "$REPEAT"); do
    sample > "$OUT/$cfg-run$r.csv" & SAMPLER=$!
    if ! k6 run --quiet -e BASES="$bases" -e VUS="$VUS" -e STEADY="$STEADY" \
      --summary-export "$OUT/$cfg-run$r.json" "$TOOL/member-read-multi.js" > "$OUT/$cfg-run$r.log" 2>&1; then
      kill $SAMPLER
      echo "✗ $cfg 측정 $r회차 실패 — $OUT/$cfg-run$r.log" >&2
      exit 1
    fi
    kill $SAMPLER
  done
  stop_instances
done
echo "결과: $OUT"
python3 "$TOOL/pool_report.py" "$OUT"
