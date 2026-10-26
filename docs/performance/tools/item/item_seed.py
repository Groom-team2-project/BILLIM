"""격리된 billim-perf DB에 B파트(물건·검색) 성능 측정 데이터를 만든다.

- seed.py 없이도 혼자 동작한다 (동네·장소·조회 회원·로그인 세션까지 직접 생성).
- MySQL 재시작과 백엔드 Flyway 마이그레이션이 끝날 때까지 기다린 뒤 실행한다.
- 난수 없이 번호 계산식으로 만들어 몇 번을 실행해도 같은 데이터가 된다 (1차·2차 비교용).
- 데이터가 이미 있으면 다시 넣지 않고, 세션 갱신과 결과 파일(local/item-fixtures.json)만 다시 쓴다.

실행: python3 docs/performance/tools/item/item_seed.py
초기화: docker compose -f docs/performance/tools/item/compose.yaml down -v && rm -rf docs/performance/tools/item/local
"""

import hashlib
import json
import os
import shlex
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
COMPOSE = HERE / "compose.yaml"
OUTPUT = HERE / "local" / "item-fixtures.json"
DATABASE = "billim_perf"

# ── 규모 (문서 1번 표와 같게 유지) ──────────────────────────────
MEMBER_COUNT = 1_000           # 같은 동네 회원
PUBLIC_ITEM_COUNT = 100_000    # 공개 물건
MINE_ITEM_COUNT = 2_000        # 조회 회원 본인 물건 (5개 중 1개는 숨김)
RENTAL_COUNT = 200_000         # 대여 (물건당 2건, 기간 검색 NOT EXISTS 비용 확인용)
PLACE_COUNT = 10

# ── 다른 시드와 겹치지 않는 ID 구간 ─────────────────────────────
# 팀 seed.py 는 동네·장소 910000 을 그냥 INSERT 하므로, 실행 순서와 무관하도록 다른 번호를 쓴다
COMMUNITY_ID = 910_100
PLACE_ID_BASE = 910_100
VIEWER_ID = 1_999_999
MEMBER_ID_BASE = 2_000_000
ITEM_ID_BASE = 3_000_000
MINE_ITEM_ID_BASE = 3_200_000
MEDIA_ID_BASE = 4_000_000
RENTAL_ID_BASE = 5_000_000

VIEWER_TOKEN = "perf-item-viewer-token"   # k6 쿠키 값. DB에는 SHA-256만 저장
VIEWER_CSRF = "perf-item-viewer-csrf"

REQUIRED_TABLES = ("communities", "community_places", "members", "community_memberships", "auth_sessions",
                   "categories", "items", "media_files", "item_images", "rentals", "appointment_slots")


def mysql_command() -> list:
    # 테스트용으로 PERF_MYSQL_CMD 환경 변수로 접속 명령을 바꿀 수 있다
    override = os.environ.get("PERF_MYSQL_CMD")
    if override:
        return shlex.split(override)
    return ["docker", "compose", "-f", str(COMPOSE), "exec", "-T", "mysql", "sh", "-c",
            f'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N -B {DATABASE}']


def run_sql(statement: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(mysql_command(), input=statement, text=True, capture_output=True, check=check)


def query_one(statement: str) -> str:
    result = run_sql(statement)
    return result.stdout.strip()


def wait_until(label: str, probe, timeout_s: int) -> None:
    deadline = time.time() + timeout_s
    last_error = ""
    while time.time() < deadline:
        try:
            if probe():
                print(f"  ✓ {label}")
                return
        except subprocess.CalledProcessError as e:
            last_error = (e.stderr or "").strip().splitlines()[-1:] or [""]
            last_error = last_error[0]
        time.sleep(3)
    raise SystemExit(f"✗ {label} 대기 시간 초과 ({timeout_s}초). {last_error}\n"
                     f"  docker compose -f docs/performance/tools/item/compose.yaml logs backend --tail 50 으로 상태를 확인하세요.")


def schema_ready() -> bool:
    names = ",".join(f"'{t}'" for t in REQUIRED_TABLES)
    count = query_one(f"SELECT COUNT(*) FROM information_schema.tables "
                      f"WHERE table_schema = DATABASE() AND table_name IN ({names});")
    if int(count or 0) != len(REQUIRED_TABLES):
        return False
    return int(query_one("SELECT COUNT(*) FROM categories;") or 0) >= 10


def numbers(digits: int) -> str:
    # 0 .. 10^digits - 1 (재귀 CTE 깊이 제한 없이 만드는 숫자 원천)
    digit = ("(SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 "
             "UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)")
    aliases = [f"d{i}" for i in range(digits)]
    terms = " + ".join(f"{a}.d * {10 ** i}" for i, a in enumerate(aliases))
    joins = " CROSS JOIN ".join(f"{digit} {a}" for a in aliases)
    return f"SELECT {terms} AS n FROM {joins}"


def seed_sql() -> str:
    six = numbers(6)
    return f"""
SET @now = UTC_TIMESTAMP(6);
SET @today = DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00'));
START TRANSACTION;

-- B파트 전용 동네와 공용 장소 10곳 (팀 seed.py 의 910000 과 별도)
INSERT IGNORE INTO communities
    (id, name, display_address, center_latitude, center_longitude, verification_radius_m, status, created_at, updated_at)
VALUES ({COMMUNITY_ID}, 'B파트 성능 동네', '성능 측정 전용', 35.0005000, 127.0005000, 1000, 'ACTIVE', @now, @now);

INSERT IGNORE INTO community_places
    (id, community_id, name, latitude, longitude, guide, active, created_at, updated_at)
SELECT {PLACE_ID_BASE} + n, {COMMUNITY_ID}, CONCAT('성능 측정 장소 ', n + 1),
       35.0000000 + n * 0.0001000, 127.0000000 + n * 0.0001000, 'B파트 성능 측정 전용', TRUE, @now, @now
FROM ({numbers(1)}) x WHERE n < {PLACE_COUNT};

-- 조회 회원(측정 주체)과 같은 동네 회원
INSERT INTO members (id, display_name, role, status, active_community_id, version, created_at, updated_at)
VALUES ({VIEWER_ID}, '물건 성능 조회자', 'USER', 'ACTIVE', {COMMUNITY_ID}, 0, @now, @now);

INSERT INTO members (id, display_name, role, status, active_community_id, version, created_at, updated_at)
SELECT {MEMBER_ID_BASE} + n, CONCAT('물건 성능 회원 ', n), 'USER', 'ACTIVE', {COMMUNITY_ID}, 0, @now, @now
FROM ({numbers(4)}) x WHERE n < {MEMBER_COUNT};

INSERT INTO community_memberships
    (member_id, community_id, status, verified_at, verification_expires_at, created_at, updated_at)
SELECT id, {COMMUNITY_ID}, 'ACTIVE', @now, @now + INTERVAL 30 DAY, @now, @now
FROM members WHERE id = {VIEWER_ID} OR id BETWEEN {MEMBER_ID_BASE} AND {MEMBER_ID_BASE + MEMBER_COUNT - 1};

-- 공개 물건 (대여 가능 기간: 오늘 - 150일 ~ 오늘 + 30~89일, 생성 시각은 최근 365일에 분산)
INSERT INTO items
    (id, owner_id, community_id, category_id, place_id, title, description,
     available_start_date, available_end_date, visibility, version, created_at, updated_at)
SELECT {ITEM_ID_BASE} + x.n, {MEMBER_ID_BASE} + MOD(x.n, {MEMBER_COUNT}), {COMMUNITY_ID}, c.id,
       {PLACE_ID_BASE} + MOD(x.n, {PLACE_COUNT}),
       CONCAT(ELT(MOD(x.n, 10) + 1, '충전 드릴 세트', '캠핑 텐트', '여행용 캐리어', '아기 의자', '통기타',
                  '배드민턴 라켓', '대형 냄비', '스팀 청소기', '접이식 사다리', '생활 물품'), ' ', x.n),
       CONCAT('B파트 검색 성능 측정을 위한 물건 설명 ', x.n),
       @today - INTERVAL 150 DAY, @today + INTERVAL (30 + MOD(x.n, 60)) DAY, 'PUBLIC', 0,
       @now - INTERVAL MOD(x.n, 365) DAY - INTERVAL MOD(x.n, 1440) MINUTE, @now
FROM ({six}) x
JOIN categories c ON c.sort_order = MOD(x.n, 10)
WHERE x.n < {PUBLIC_ITEM_COUNT};

-- 조회 회원 본인 물건 (5개 중 1개 숨김)
INSERT INTO items
    (id, owner_id, community_id, category_id, place_id, title, description,
     available_start_date, available_end_date, visibility, version, created_at, updated_at)
SELECT {MINE_ITEM_ID_BASE} + x.n, {VIEWER_ID}, {COMMUNITY_ID}, c.id, {PLACE_ID_BASE} + MOD(x.n, {PLACE_COUNT}),
       CONCAT('내 물건 ', x.n), '내 물건 목록 성능 측정용',
       @today - INTERVAL 150 DAY, @today + INTERVAL 30 DAY,
       IF(MOD(x.n, 5) = 0, 'HIDDEN', 'PUBLIC'), 0,
       @now - INTERVAL MOD(x.n, 365) DAY, @now
FROM ({numbers(4)}) x
JOIN categories c ON c.sort_order = MOD(x.n, 10)
WHERE x.n < {MINE_ITEM_COUNT};

-- 대표 사진 메타데이터 (파일 본문은 만들지 않음)
INSERT INTO media_files
    (id, uploader_id, storage_key, mime_type, byte_size, width, height, status, expires_at, created_at, updated_at)
SELECT {MEDIA_ID_BASE} + x.n, {MEMBER_ID_BASE} + MOD(x.n, {MEMBER_COUNT}), CONCAT('perf/item-', x.n, '.jpg'),
       'image/jpeg', 1024, 800, 600, 'ATTACHED', @now + INTERVAL 30 DAY, @now, @now
FROM ({six}) x WHERE x.n < {PUBLIC_ITEM_COUNT};

INSERT INTO item_images (item_id, media_file_id, sort_order, created_at, updated_at)
SELECT {ITEM_ID_BASE} + x.n, {MEDIA_ID_BASE} + x.n, 0, @now, @now
FROM ({six}) x WHERE x.n < {PUBLIC_ITEM_COUNT};

-- 대여: 대여 r 은 물건 (r mod 공개 물건 수)의 k = r div 공개 물건 수 번째 창.
-- 창 시작 = 오늘 + 1일 + 7k + (r mod 5), 길이 1~3일 → 같은 물건의 창은 겹치지 않음 (승인 겹침 금지 유지)
-- 측정 기간(오늘+1 ~ +7일)과 겹치는 창이 섞이도록 상태를 나눔: 승인 40% · 요청 대기 40% · 거절 20%
INSERT INTO rentals
    (id, item_id, owner_id, borrower_id, community_id, chat_room_id, start_date, end_date, status,
     request_expires_at, due_at, item_title_snapshot, place_name_snapshot, place_id, reason,
     approved_at, handed_over_at, returned_at, version, created_at, updated_at)
SELECT {RENTAL_ID_BASE} + r.n, i.id, i.owner_id,
       {MEMBER_ID_BASE} + MOD(i.owner_id - {MEMBER_ID_BASE} + 1, {MEMBER_COUNT}),
       {COMMUNITY_ID}, NULL, r.s, r.s + INTERVAL MOD(r.n, 3) DAY,
       CASE WHEN MOD(r.n, 5) < 2 THEN 'APPROVED' WHEN MOD(r.n, 5) < 4 THEN 'REQUESTED' ELSE 'REJECTED' END,
       TIMESTAMP(r.s + INTERVAL 1 DAY) - INTERVAL 9 HOUR,
       TIMESTAMP(r.s + INTERVAL MOD(r.n, 3) + 1 DAY) - INTERVAL 9 HOUR,
       i.title, p.name, i.place_id,
       IF(MOD(r.n, 5) = 4, '성능 측정용 거절', NULL),
       IF(MOD(r.n, 5) < 2, @now, NULL), NULL, NULL, 0, @now, @now
FROM (SELECT n, @today + INTERVAL (1 + 7 * (n DIV {PUBLIC_ITEM_COUNT}) + MOD(n, 5)) DAY AS s
      FROM ({six}) x WHERE n < {RENTAL_COUNT}) r
JOIN items i ON i.id = {ITEM_ID_BASE} + MOD(r.n, {PUBLIC_ITEM_COUNT})
JOIN community_places p ON p.id = i.place_id;

-- 승인 거래의 전달·반납 약속 슬롯 (V13 백필과 같은 규칙)
INSERT INTO appointment_slots (rental_id, kind, version, created_at, updated_at)
SELECT r.id, k.kind, 0, @now, @now
FROM rentals r CROSS JOIN (SELECT 'PICKUP' AS kind UNION ALL SELECT 'RETURN') k
WHERE r.id >= {RENTAL_ID_BASE} AND r.status = 'APPROVED';

COMMIT;
"""


def session_sql() -> str:
    token_hash = hashlib.sha256(VIEWER_TOKEN.encode()).hexdigest()
    return f"""
SET @now = UTC_TIMESTAMP(6);
DELETE FROM auth_sessions WHERE member_id = {VIEWER_ID};
INSERT INTO auth_sessions
    (member_id, token_hash, csrf_token, last_seen_at, expires_at, absolute_expires_at, revoked_at, created_at, updated_at)
VALUES ({VIEWER_ID}, '{token_hash}', '{VIEWER_CSRF}', @now, @now + INTERVAL 24 HOUR, @now + INTERVAL 24 HOUR, NULL, @now, @now);
"""


def main() -> None:
    print("1/4 MySQL·스키마 준비 대기")
    wait_until("MySQL 접속", lambda: query_one("SELECT 1;") == "1", 120)
    wait_until("백엔드 마이그레이션 완료 (테이블·카테고리)", schema_ready, 240)

    seeded = int(query_one(f"SELECT COUNT(*) FROM items WHERE id = {ITEM_ID_BASE};") or 0) == 1
    if seeded:
        print("2/4 데이터가 이미 있음 → 다시 넣지 않음")
    else:
        print("2/4 데이터 생성 중 (1~2분 걸릴 수 있음)")
        started = time.time()
        result = run_sql(seed_sql(), check=False)
        if result.returncode != 0:
            raise SystemExit(f"✗ 데이터 생성 실패\n{result.stderr.strip()}")
        print(f"  ✓ {time.time() - started:.0f}초")

    print("3/4 조회 회원 로그인 세션 갱신 (24시간 유효)")
    result = run_sql(session_sql(), check=False)
    if result.returncode != 0:
        raise SystemExit(f"✗ 세션 생성 실패\n{result.stderr.strip()}")

    counts = query_one(f"""
SELECT CONCAT_WS(',',
  (SELECT COUNT(*) FROM items WHERE id >= {ITEM_ID_BASE} AND id < {MINE_ITEM_ID_BASE}),
  (SELECT COUNT(*) FROM items WHERE owner_id = {VIEWER_ID}),
  (SELECT COUNT(*) FROM rentals WHERE id >= {RENTAL_ID_BASE}),
  (SELECT COUNT(*) FROM members WHERE id = {VIEWER_ID} OR id BETWEEN {MEMBER_ID_BASE} AND {MEMBER_ID_BASE + MEMBER_COUNT - 1}),
  (SELECT MIN(id) FROM categories));""").split(",")
    public_items, mine_items, rentals, members, first_category = (int(v) for v in counts)

    print("4/4 결과 파일 쓰기")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    fixture = {
        "viewer": {"memberId": VIEWER_ID, "token": VIEWER_TOKEN, "csrfToken": VIEWER_CSRF},
        "targetMemberId": MEMBER_ID_BASE,
        "detailItemId": ITEM_ID_BASE,
        "categoryId": first_category,
        "placeId": PLACE_ID_BASE,
        "communityId": COMMUNITY_ID,
        "counts": {"members": members, "publicItems": public_items, "mineItems": mine_items, "rentals": rentals},
    }
    OUTPUT.write_text(json.dumps(fixture, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n완료: 회원 {members:,} · 공개 물건 {public_items:,} · 내 물건 {mine_items:,} · 대여 {rentals:,}")
    print(f"결과 파일: {OUTPUT.relative_to(HERE.parent)}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
