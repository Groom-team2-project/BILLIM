#!/usr/bin/env bash
# B파트 물건 조회 성능 측정을 CASE마다 반복 실행하고 결과를 한 폴더에 모은다.
# 순서: 연결 확인(1 VU) → CASE마다 워밍업 10초(기록 안 함) → 같은 조건 REPEAT회 측정 → 표 정리
#
# 사용: bash docs/performance/tools/item/item_bench.sh
# 옵션: CASES="search-latest map" VUS=10 DURATION=30s REPEAT=3 bash docs/performance/tools/item/item_bench.sh
set -uo pipefail
cd "$(dirname "$0")/../../../.."

TOOL="docs/performance/tools/item"
COMPOSE="$TOOL/compose.yaml"
CASES=${CASES:-"search-latest search-keyword search-category search-period search-nearest search-deep-page item-detail mine neighbor map home"}
VUS=${VUS:-10}
DURATION=${DURATION:-30s}
REPEAT=${REPEAT:-3}
COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
OUT="$TOOL/local/results/$(date +%Y%m%d-%H%M%S)-${COMMIT}"

if [ ! -f "$TOOL/local/item-fixtures.json" ]; then
  echo "local/item-fixtures.json 이 없습니다. 먼저 python3 $TOOL/item_seed.py 를 실행하세요."
  exit 1
fi
mkdir -p "$OUT"
echo "커밋 ${COMMIT} · VU ${VUS} · ${DURATION} · ${REPEAT}회 → ${OUT}"

k6run() {
  docker compose -f "$COMPOSE" run --rm -e CASE="$1" -e VUS="$2" -e DURATION="$3" \
    k6 run --quiet /scripts/item-read.js
}

# 백엔드가 막 (재)시작된 직후면 요청이 거절되므로, 응답할 때까지 기다린다 (팀 README 기준: /api/v1/auth/csrf 200)
echo "▶ 백엔드 준비 대기"
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/api/v1/auth/csrf || true)
  [ "$code" = "200" ] && break
  sleep 3
done
if [ "$code" != "200" ]; then
  echo "✗ 3분 동안 백엔드가 응답하지 않습니다. 로그: docker compose -f $COMPOSE logs backend --tail 50"
  exit 1
fi
echo "  ✓ 응답 확인"

echo "▶ 연결 확인 (1 VU · 5초)"
if ! k6run search-latest 1 5s > "$OUT/smoke.txt" 2>&1 || ! grep -Eq '"failRate":0[,}]' "$OUT/smoke.txt"; then
  cat "$OUT/smoke.txt"
  echo "✗ 연결 확인 실패. 백엔드 로그: docker compose -f $COMPOSE logs backend --tail 50"
  exit 1
fi
echo "  ✓ 통과"

for c in $CASES; do
  echo "▶ ${c}"
  k6run "$c" "$VUS" 10s > "$OUT/${c}-warmup.txt" 2>&1 || true
  for i in $(seq 1 "$REPEAT"); do
    k6run "$c" "$VUS" "$DURATION" > "$OUT/${c}-${i}.txt" 2>&1
    line=$(grep '^  응답' "$OUT/${c}-${i}.txt" || echo "  결과 없음 (파일 확인: $OUT/${c}-${i}.txt)")
    echo "  ${i}/${REPEAT}${line}"
  done
done

python3 "$TOOL/item_report.py" "$OUT" | tee "$OUT/report.md"
echo
echo "원본: ${OUT}  ·  표: ${OUT}/report.md (docs/performance 측정 문서 2번 표에 붙여 넣기)"
