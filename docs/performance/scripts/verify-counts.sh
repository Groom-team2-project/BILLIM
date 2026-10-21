#!/bin/bash
# 환경 무관 근거 재확인 — 요청당 쿼리 수, 인증 고정 비용, 삭제 조건 색인.
# 응답시간·처리량은 머신 의존이라 제외.
#
#   사전: docker compose up -d · SQL 로그를 켠 상태로 앱 기동(local 프로필 기본값)
#   실행: bash docs/performance/scripts/verify-counts.sh <앱 로그 경로>
set -uo pipefail

LOG="${1:-}"
[ -z "$LOG" ] && { echo "사용법: $0 <앱 로그 경로>"; exit 2; }
[ -f "$LOG" ] || { echo "로그 없음: $LOG"; exit 2; }
command -v docker >/dev/null || { echo "docker 필요"; exit 2; }
: "${MYSQL_PASSWORD:?backend/.env를 export 하고 실행}"

DB="docker exec -i billim-mysql-1 mysql -ubillim -p$MYSQL_PASSWORD billim -N"
PASS=0; FAIL=0
check() { # 이름 기대값 실측값
  if [ "$2" = "$3" ]; then printf '  OK   %-28s %s\n' "$1" "$3"; PASS=$((PASS+1))
  else printf '  FAIL %-28s 기대 %s · 실측 %s\n' "$1" "$2" "$3"; FAIL=$((FAIL+1)); fi
}

echo "1. 삭제 조건 색인 — 정리 배치가 읽는 행"
# 정리 배치의 실제 문장은 DELETE — SELECT 계획과 옵티마이저 선택이 다를 수 있어 DELETE를 직접 검사
PLAN=$($DB -e "explain delete from auth_sessions where absolute_expires_at < now() - interval 7 day;" 2>/dev/null)
[ -n "$PLAN" ] || { echo "  EXPLAIN 실패 — DB 접속 확인"; exit 1; }
check "EXPLAIN type" "range" "$(echo "$PLAN" | awk '{print $5}')"
check "사용 색인" "ix_auth_sessions_absolute_expires" "$(echo "$PLAN" | awk '{print $7}')"

echo "2. 요청당 쿼리 수 · 인증 고정 비용"
# 전제 — 테스트 대상 회원이 없으면 이후 검사가 전부 무의미하므로 중단
for id in 3 4; do
  [ "$($DB -e "select count(*) from members where id=$id and status='ACTIVE';" 2>/dev/null)" = "1" ] \
    || { echo "  회원 $id(ACTIVE) 없음 — 시드 적재 후 다시 실행"; exit 2; }
done
TOKEN=$(openssl rand -hex 32)
HASH=$(printf '%s' "$TOKEN" | shasum -a 256 | cut -d' ' -f1)
$DB -e "delete from auth_sessions where csrf_token='verify';
insert into auth_sessions (member_id, token_hash, csrf_token, last_seen_at, expires_at, absolute_expires_at, created_at, updated_at)
values (3, '$HASH', 'verify', now(6), now(6) + interval 7 day, now(6) + interval 30 day, now(6), now(6));" 2>/dev/null
trap '$DB -e "delete from auth_sessions where csrf_token=\"verify\";" 2>/dev/null' EXIT

curl -s -o /dev/null -H "Cookie: BILLIM_SESSION=$TOKEN" localhost:8080/api/v1/members/me; sleep 1
while IFS='|' read -r name path want; do
  # 로그 기록이 늦어 0으로 잡히는 경우가 있어 최대 3회 시도
  total=0; auth=0; code=000
  for try in 1 2 3; do
    before=$(wc -l < "$LOG")
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H "Cookie: BILLIM_SESSION=$TOKEN" "localhost:8080$path")
    sleep 2
    # format_sql=true면 SQL이 여러 줄 출력 — 로거 줄부터 다음 로그 줄까지를 한 문장으로 묶어 집계
    counts=$(tail -n +$((before+1)) "$LOG" | awk '
      /^[0-9]{4}-[0-9]{2}-[0-9]{2}/ {
        if (inStmt) { total++; if (buf ~ /auth_sessions/) auth++ }
        inStmt = ($0 ~ /nio-8080-exec/ && $0 ~ /org\.hibernate\.SQL/); buf = inStmt ? $0 : ""
        next
      }
      inStmt { buf = buf " " $0 }
      END { if (inStmt) { total++; if (buf ~ /auth_sessions/) auth++ }
            printf "%d %d", total+0, auth+0 }')
    total=${counts% *}
    auth=${counts#* }
    [ "$total" -gt 0 ] && break
  done
  if [ "$code" != "200" ]; then
    printf '  FAIL %-28s HTTP %s (200 기대)\n' "$name" "$code"; FAIL=$((FAIL+1)); continue
  fi
  check "$name 전체" "$want" "$total"
  check "$name 인증" "1" "$auth"
done <<'LIST'
내 프로필|/api/v1/members/me|5
이웃 프로필|/api/v1/members/4|8
알림 미읽음|/api/v1/notifications/unread-count|2
채팅 미읽음|/api/v1/chat/unread-count|2
카테고리|/api/v1/categories|2
LIST

echo
echo "통과 $PASS · 실패 $FAIL"
[ "$FAIL" -eq 0 ] || exit 1
