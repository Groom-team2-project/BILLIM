"""공유 MySQL·Redis에 연결한 서버 두 대에서 채팅과 알림 결과를 검증한다."""

import json
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import pymysql
import websocket


FIXTURE = Path("/scripts/local/fixtures-multi.json")
OUTPUT = Path("/results/multi-instance-result-20261031.json")
SERVERS = ("backend-a", "backend-b")


def http(server, path, user, method="GET", body=None):
    headers = {"Cookie": f"BILLIM_SESSION={user['token']}", "X-CSRF-TOKEN": user["csrf"]}
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(
        f"http://{server}:8080{path}", data=None if body is None else json.dumps(body).encode(),
        headers=headers, method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()[:300]


def send(server, pair, user, message_id, body):
    status, result = http(server, f"/api/v1/chat/rooms/{pair['roomId']}/messages", user,
                          "POST", {"clientMessageId": message_id, "body": body})
    if status != 201:
        raise AssertionError(f"send {server}: HTTP {status}, {result}")
    return result


def database():
    return pymysql.connect(host="mysql", user="billim_perf", password="local_perf_only",
                           database="billim_perf", autocommit=True, connect_timeout=10)


def ready(pair):
    for attempt in range(90):
        try:
            for server in SERVERS:
                status, _ = http(server, "/api/v1/chat/rooms?filter=ALL&page=0&size=20", pair["owner"])
                if status != 200:
                    raise AssertionError(f"{server} not ready: {status}")
            return
        except (OSError, AssertionError):
            time.sleep(1)
    raise AssertionError("Both backend instances did not become ready")


def cross_instance_delivery(pairs):
    latencies = []
    for pair in pairs:
        for receiver, sender, sender_user, receiver_user in (
            ("backend-b", "backend-a", pair["owner"], pair["requester"]),
            ("backend-a", "backend-b", pair["requester"], pair["owner"]),
        ):
            ws = websocket.create_connection(
                f"ws://{receiver}:8080/ws/chat?roomId={pair['roomId']}", timeout=6,
                header=[f"Cookie: BILLIM_SESSION={receiver_user['token']}"],
                origin=f"http://{receiver}:8080",
            )
            try:
                message_id = str(uuid.uuid4())
                started = time.monotonic()
                send(sender, pair, sender_user, message_id, "cross-instance delivery")
                deadline = time.monotonic() + 6
                while time.monotonic() < deadline:
                    received = json.loads(ws.recv())
                    if received.get("clientMessageId") == message_id:
                        latencies.append(round((time.monotonic() - started) * 1000, 1))
                        break
                else:
                    raise AssertionError(f"message not received on {receiver} from {sender}")
            finally:
                ws.close()
    return {"directions": len(latencies), "p95_ms": sorted(latencies)[int((len(latencies)-1)*.95)],
            "max_ms": max(latencies), "all_ms": latencies}


def concurrent_send(pair):
    requests = [(SERVERS[index % 2], pair["owner"] if index % 2 == 0 else pair["requester"],
                 str(uuid.uuid4())) for index in range(64)]
    def one(request):
        server, user, message_id = request
        return send(server, pair, user, message_id, f"concurrent {message_id}")
    with ThreadPoolExecutor(max_workers=16) as pool:
        responses = list(pool.map(one, requests))
    sequences = [int(result["sequence"]) for result in responses]
    ids = [result["id"] for result in responses]
    if len(set(sequences)) != 64 or len(set(ids)) != 64:
        raise AssertionError("Concurrent sends returned duplicate sequence or message id")
    with database() as connection, connection.cursor() as cursor:
        placeholders = ",".join(["%s"] * len(ids))
        cursor.execute(f"SELECT sequence,client_message_id FROM messages WHERE id IN ({placeholders})", ids)
        rows = cursor.fetchall()
        cursor.execute("SELECT next_sequence FROM chat_rooms WHERE id=%s", (pair["roomId"],))
        next_sequence = cursor.fetchone()[0]
    if len(rows) != 64 or sorted(row[0] for row in rows) != sorted(sequences):
        raise AssertionError("DB messages differ from concurrent HTTP responses")
    if sorted(sequences) != list(range(min(sequences), max(sequences)+1)):
        raise AssertionError("Concurrent message sequences have a gap")
    if next_sequence != max(sequences) + 1:
        raise AssertionError("Chat room next_sequence is inconsistent")
    return {"requests": 64, "distinct_sequences": len(set(sequences)),
            "first_sequence": min(sequences), "last_sequence": max(sequences)}


def duplicate_key(pair):
    key = str(uuid.uuid4())
    with ThreadPoolExecutor(max_workers=2) as pool:
        responses = list(pool.map(
            lambda server: send(server, pair, pair["owner"], key, "same-key concurrent retry"), SERVERS,
        ))
    if responses[0]["id"] != responses[1]["id"] or responses[0]["sequence"] != responses[1]["sequence"]:
        raise AssertionError("Same client key created two messages")
    with database() as connection, connection.cursor() as cursor:
        cursor.execute("SELECT COUNT(*) FROM messages WHERE room_id=%s AND sender_id=%s AND client_message_id=%s",
                       (pair["roomId"], pair["owner"]["memberId"], key))
        count = cursor.fetchone()[0]
    if count != 1:
        raise AssertionError(f"Same client key has {count} DB rows")
    return {"http_responses": 2, "db_rows": count, "message_id": responses[0]["id"]}


def outbox_two_workers(pairs):
    count = 300
    prefix = "perf-multi-" + uuid.uuid4().hex[:10] + "-"
    display = {"actorName": None, "itemTitle": None, "startDate": None, "endDate": None,
               "placeName": None, "appointmentAt": None, "label": None}
    rows = []
    for index in range(count):
        recipient = pairs[index % len(pairs)]["requester"]["memberId"]
        payload = json.dumps({"recipients": [{"memberId": recipient, "role": "REPORTER",
                                                "targetType": None, "targetId": None}],
                              "targetType": "REPORT", "targetId": 990000 + index,
                              "display": display}, separators=(",", ":"))
        rows.append((prefix + str(index), 990000 + index, payload))
    with database() as connection, connection.cursor() as cursor:
        connection.begin()
        cursor.executemany(
            "INSERT INTO outbox_events (event_key,event_type,aggregate_type,aggregate_id,"
            "aggregate_version,payload,status,attempts,available_at,created_at,updated_at) "
            "VALUES (%s,'REPORT_RESOLVED','REPORT',%s,1,%s,'PENDING',0,UTC_TIMESTAMP(6),"
            "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", rows,
        )
        connection.commit()
    started = time.monotonic()
    while time.monotonic() - started < 120:
        with database() as connection, connection.cursor() as cursor:
            cursor.execute("SELECT status,COUNT(*) FROM outbox_events WHERE event_key LIKE %s GROUP BY status",
                           (prefix + "%",))
            states = dict(cursor.fetchall())
            if states.get("FAILED"):
                raise AssertionError(f"Outbox failures: {states}")
            if states.get("DONE") == count:
                cursor.execute("SELECT COUNT(*),COUNT(DISTINCT n.event_id) FROM notifications n "
                               "JOIN outbox_events e ON e.id=n.event_id WHERE e.event_key LIKE %s",
                               (prefix + "%",))
                notifications, distinct_events = cursor.fetchone()
                cursor.execute("SELECT COUNT(*) FROM event_deliveries d JOIN outbox_events e "
                               "ON e.id=d.event_id WHERE e.event_key LIKE %s", (prefix + "%",))
                deliveries = cursor.fetchone()[0]
                if (notifications, distinct_events, deliveries) != (count, count, count):
                    raise AssertionError(f"Outbox duplicates/missing: {notifications},"
                                         f"{distinct_events},{deliveries} vs {count}")
                return {"events": count, "done": count, "notifications": notifications,
                        "distinct_events": distinct_events, "deliveries": deliveries,
                        "elapsed_s": round(time.monotonic() - started, 2)}
        time.sleep(.5)
    raise AssertionError("Outbox did not drain within 120 seconds")


def main():
    result = {"environment": "2 Spring Boot / 1 MySQL / 1 Redis", "status": "running"}
    try:
        pairs = json.loads(FIXTURE.read_text())["pairs"]
        ready(pairs[0])
        time.sleep(2)
        result["cross_instance_delivery"] = cross_instance_delivery(pairs)
        print("cross-instance delivery complete", flush=True)
        result["concurrent_send"] = concurrent_send(pairs[0])
        print("concurrent send complete", flush=True)
        result["duplicate_client_key"] = duplicate_key(pairs[0])
        print("duplicate-key retry complete", flush=True)
        result["outbox_two_workers"] = outbox_two_workers(pairs)
        print("two-worker outbox complete", flush=True)
        result["status"] = "passed"
    except Exception as error:
        result["status"] = "failed"
        result["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"result: {OUTPUT}; status: {result['status']}", flush=True)


if __name__ == "__main__":
    main()
