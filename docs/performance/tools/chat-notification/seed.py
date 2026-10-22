"""Create isolated performance fixtures after Flyway has migrated billim_perf."""

import hashlib
import json
import os
import secrets
import subprocess
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


HERE = Path(__file__).resolve().parent
COMPOSE = HERE / "compose.yaml"
OUTPUT = HERE / "local" / "fixtures.json"
PAIRS = int(os.environ.get("PERF_PAIRS", "8"))
NOTIFICATIONS_PER_MEMBER = int(os.environ.get("PERF_NOTIFICATIONS_PER_MEMBER", "100"))


def sql_string(value):
    return "'" + value.replace("'", "''") + "'"


def main():
    refresh_only = len(sys.argv) == 2 and sys.argv[1] == "--refresh-csrf"
    if not 1 <= PAIRS <= 500 or not 0 <= NOTIFICATIONS_PER_MEMBER <= 1000:
        raise SystemExit("PERF_PAIRS must be 1..500 and PERF_NOTIFICATIONS_PER_MEMBER 0..1000")
    if not COMPOSE.exists() or "name: billim-perf" not in COMPOSE.read_text():
        raise SystemExit("Isolated billim-perf compose file is required")
    if OUTPUT.exists() and not refresh_only:
        raise SystemExit(f"Fixture already exists: {OUTPUT}. Reset the isolated stack before reseeding.")

    if refresh_only:
        if not OUTPUT.exists():
            raise SystemExit(f"Fixture does not exist: {OUTPUT}. Seed the isolated stack first.")
        fixtures = json.loads(OUTPUT.read_text(encoding="utf-8"))["pairs"]
        refresh_and_save_csrf(fixtures)
        return

    statements = ["START TRANSACTION;"]
    statements.append("INSERT INTO communities (id,name,display_address,center_latitude,center_longitude,verification_radius_m,status,created_at,updated_at) VALUES (910000,'Performance Test Community','Local only',35.0,127.0,1000,'ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
    statements.append("INSERT INTO community_places (id,community_id,name,latitude,longitude,active,created_at,updated_at) VALUES (910000,910000,'Performance Test Place',35.0,127.0,TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
    fixtures = []
    event_id = 920000
    notification_id = 930000

    for pair in range(PAIRS):
        room_id = 911000 + pair
        item_id = 912000 + pair
        users = []
        for side in range(2):
            member_id = 913000 + pair * 2 + side
            token = secrets.token_urlsafe(32)
            csrf = secrets.token_urlsafe(32)
            statements.append(f"INSERT INTO members (id,display_name,role,status,active_community_id,version,created_at,updated_at) VALUES ({member_id},'Perf User {member_id}','USER','ACTIVE',910000,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
            statements.append(f"INSERT INTO community_memberships (member_id,community_id,status,verified_at,verification_expires_at,created_at,updated_at) VALUES ({member_id},910000,'ACTIVE',UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 DAY),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
            statements.append(f"INSERT INTO auth_sessions (member_id,token_hash,csrf_token,last_seen_at,expires_at,absolute_expires_at,created_at,updated_at) VALUES ({member_id},'{hashlib.sha256(token.encode()).hexdigest()}',{sql_string(csrf)},UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 2 HOUR),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 24 HOUR),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
            users.append({"memberId": member_id, "token": token, "csrf": csrf})

        statements.append(f"INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,available_start_date,available_end_date,visibility,version,created_at,updated_at) VALUES ({item_id},{users[0]['memberId']},910000,(SELECT id FROM categories WHERE code='LIFE'),910000,'Performance Test Item',UTC_DATE(),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),'PUBLIC',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
        statements.append(f"INSERT INTO chat_rooms (id,item_id,owner_id,requester_id,next_sequence,created_at,updated_at) VALUES ({room_id},{item_id},{users[0]['memberId']},{users[1]['memberId']},1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
        for user in users:
            statements.append(f"INSERT INTO chat_participants (room_id,member_id,last_read_sequence,created_at,updated_at) VALUES ({room_id},{user['memberId']},0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")

        ids = []
        for _ in range(NOTIFICATIONS_PER_MEMBER):
            event_id += 1
            notification_id += 1
            statements.append(f"INSERT INTO outbox_events (id,event_key,event_type,aggregate_type,aggregate_id,aggregate_version,payload,status,attempts,available_at,processed_at,created_at,updated_at) VALUES ({event_id},'perf:{event_id}','REPORT_RESOLVED','REPORT',{event_id},1,'{{}}','DONE',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
            statements.append(f"INSERT INTO notifications (id,recipient_id,event_id,type,title,target_type,target_id,created_at,updated_at) VALUES ({notification_id},{users[1]['memberId']},{event_id},'REPORT_RESOLVED','Performance Test Notification','MEMBER_SELF',{users[1]['memberId']},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));")
            ids.append(notification_id)

        fixtures.append({"roomId": room_id, "owner": users[0], "requester": users[1], "notificationIds": ids})

    statements.append("COMMIT;")
    command = ["docker", "compose", "-f", str(COMPOSE), "exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot billim_perf']
    subprocess.run(command, input="\n".join(statements), text=True, check=True)
    save_fixture(fixtures)
    refresh_and_save_csrf(fixtures)
    print(f"Seeded {PAIRS} chat rooms and {PAIRS * NOTIFICATIONS_PER_MEMBER} notifications. Fixture saved to ignored {OUTPUT}.")


def save_fixture(fixtures):
    OUTPUT.parent.mkdir(exist_ok=True)
    OUTPUT.write_text(json.dumps({"pairs": fixtures}, indent=2), encoding="utf-8")


def refresh_and_save_csrf(fixtures):
    try:
        refresh_csrf(fixtures)
    except (OSError, ValueError, KeyError) as exc:
        raise SystemExit(
            f"Fixture is saved at {OUTPUT}, but CSRF verification failed. "
            "Start the backend and retry with: python docs/performance/tools/chat-notification/seed.py --refresh-csrf"
        ) from exc
    save_fixture(fixtures)


def refresh_csrf(fixtures):
    users = [user for pair in fixtures for user in (pair["owner"], pair["requester"])]

    def verify(user):
        request = urllib.request.Request(
            "http://localhost:18080/api/v1/auth/csrf",
            headers={"Cookie": f"BILLIM_SESSION={user['token']}"},
        )
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.load(response)["csrfToken"]

    with ThreadPoolExecutor(max_workers=8) as pool:
        for user, csrf in zip(users, pool.map(verify, users)):
            user["csrf"] = csrf


if __name__ == "__main__":
    main()
