"""Redis 장애·서버 재시작·지속 부하 중 두 인스턴스의 채팅·알림 결과를 확인한다."""

import json
import os
import threading
import time
import urllib.error
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import docker
import pymysql
import websocket


PROJECT = "billim-perf-multi-20261031"
FIXTURE = Path("/scripts/local/fixtures-multi.json")
OUTPUT = Path("/results/multi-instance-resilience-20261031.json")
SERVERS = ("backend-a", "backend-b")
SOAK_SECONDS = int(os.environ.get("SOAK_SECONDS", "600"))
WORKERS = int(os.environ.get("SOAK_WORKERS", "24"))


def database():
    return pymysql.connect(host="mysql", user="billim_perf", password="local_perf_only",
                           database="billim_perf", autocommit=True, connect_timeout=10)


def http(server, path, user, method="GET", body=None, timeout=30):
    headers = {"Cookie": f"BILLIM_SESSION={user['token']}", "X-CSRF-TOKEN": user["csrf"]}
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(
        f"http://{server}:8080{path}", data=None if body is None else json.dumps(body).encode(),
        headers=headers, method=method,
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()[:250]


def send(server, pair, user, message_id, body):
    return http(server, f"/api/v1/chat/rooms/{pair['roomId']}/messages", user, "POST",
                {"clientMessageId": message_id, "body": body})


def wait_backend(server, user, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            status, _ = http(server, "/api/v1/chat/rooms?filter=ALL&page=0&size=20", user, timeout=3)
            if status == 200:
                return
        except OSError:
            pass
        time.sleep(1)
    raise AssertionError(f"{server} did not become ready")


def container(client, service):
    name = f"{PROJECT}-{service}-1"
    value = client.containers.get(name)
    if value.labels.get("com.docker.compose.project") != PROJECT:
        raise AssertionError(f"Unexpected Docker project for {name}")
    return value


def subscriber_count(redis):
    redis.reload()
    if redis.status != "running":
        return 0
    command = redis.exec_run(["redis-cli", "PUBSUB", "NUMSUB", "billim:chat:committed"])
    if command.exit_code != 0:
        return 0
    return int(command.output.decode().strip().splitlines()[-1])


def wait_subscribers(redis, expected=2, timeout=45):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if subscriber_count(redis) >= expected:
            return
        time.sleep(1)
    raise AssertionError(f"Redis subscribers did not reach {expected}")


def ws_connect(server, pair, user):
    return websocket.create_connection(
        f"ws://{server}:8080/ws/chat?roomId={pair['roomId']}", timeout=8,
        header=[f"Cookie: BILLIM_SESSION={user['token']}"],
        origin=f"http://{server}:8080",
    )


def receive_id(ws, message_id, timeout=9):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            value = json.loads(ws.recv())
        except websocket.WebSocketTimeoutException:
            continue
        if value.get("clientMessageId") == message_id:
            return value
    raise AssertionError(f"WebSocket did not receive {message_id}")


def redis_outage(client, pair):
    redis = container(client, "redis")
    wait_subscribers(redis)
    ws = ws_connect("backend-b", pair, pair["requester"])
    latencies = []
    messages = []
    try:
        redis.stop(timeout=3)
        redis.reload()
        if redis.status != "exited":
            raise AssertionError("Redis did not stop")
        for _ in range(3):
            message_id = str(uuid.uuid4())
            started = time.monotonic()
            status, response = send("backend-a", pair, pair["owner"], message_id,
                                    "redis-outage " + message_id)
            if status != 201:
                raise AssertionError(f"Send failed with Redis stopped: {status} {response}")
            received = receive_id(ws, message_id)
            if received["id"] != response["id"]:
                raise AssertionError("Recovered WebSocket message differs from DB response")
            latencies.append(round((time.monotonic() - started) * 1000, 1))
            messages.append(message_id)
    finally:
        redis.start()
        ws.close()
    wait_subscribers(redis)
    with database() as connection, connection.cursor() as cursor:
        placeholders = ",".join(["%s"] * len(messages))
        cursor.execute(f"SELECT COUNT(*) FROM messages WHERE client_message_id IN ({placeholders})", messages)
        stored = cursor.fetchone()[0]
    if stored != len(messages):
        raise AssertionError("Redis outage messages missing or duplicated in DB")
    ws = ws_connect("backend-b", pair, pair["requester"])
    try:
        message_id = str(uuid.uuid4())
        status, response = send("backend-a", pair, pair["owner"], message_id, "redis-recovered")
        if status != 201 or receive_id(ws, message_id)["id"] != response["id"]:
            raise AssertionError("Cross-instance delivery failed after Redis restart")
    finally:
        ws.close()
    return {"during_outage_sent": len(messages), "during_outage_received": len(latencies),
            "recovery_latency_ms": latencies, "redis_subscribers_after": subscriber_count(redis),
            "after_restart_received": 1}


def server_restart(client, pair):
    backend = container(client, "backend-b")
    redis = container(client, "redis")
    ws = ws_connect("backend-b", pair, pair["requester"])
    with database() as connection, connection.cursor() as cursor:
        cursor.execute("SELECT next_sequence FROM chat_rooms WHERE id=%s", (pair["roomId"],))
        before = cursor.fetchone()[0] - 1
    backend.stop(timeout=5)
    try:
        backend.reload()
        if backend.status != "exited":
            raise AssertionError("backend-b did not stop")
        message_id = str(uuid.uuid4())
        status, response = send("backend-a", pair, pair["owner"], message_id, "backend-b-down")
        if status != 201:
            raise AssertionError(f"A could not send while B was down: {status} {response}")
    finally:
        backend.start()
        ws.close()
    wait_backend("backend-b", pair["requester"])
    wait_subscribers(redis)
    status, page = http("backend-b",
                        f"/api/v1/chat/rooms/{pair['roomId']}/messages?afterSequence={before}&limit=50",
                        pair["requester"])
    if status != 200 or not any(item.get("clientMessageId") == message_id for item in page["items"]):
        raise AssertionError("B could not fetch message committed while it was down")
    ws = ws_connect("backend-b", pair, pair["requester"])
    try:
        new_id = str(uuid.uuid4())
        status, new_response = send("backend-a", pair, pair["owner"], new_id, "backend-b-restarted")
        if status != 201 or receive_id(ws, new_id)["id"] != new_response["id"]:
            raise AssertionError("B did not receive new message after restart")
    finally:
        ws.close()
    return {"a_send_while_b_down": 1, "b_history_after_restart": 1,
            "b_live_delivery_after_restart": 1, "redis_subscribers_after": subscriber_count(redis)}


def insert_outbox(pairs, run_id, minute, count=40):
    display = {"actorName": None, "itemTitle": None, "startDate": None, "endDate": None,
               "placeName": None, "appointmentAt": None, "label": None}
    rows = []
    for index in range(count):
        recipient = pairs[index % len(pairs)]["requester"]["memberId"]
        payload = json.dumps({"recipients": [{"memberId": recipient, "role": "REPORTER",
                                                "targetType": None, "targetId": None}],
                              "targetType": "REPORT", "targetId": 990000 + minute * 100 + index,
                              "display": display}, separators=(",", ":"))
        rows.append((f"perf-soak-{run_id}-{minute}-{index}", 990000 + minute * 100 + index,
                     payload))
    with database() as connection, connection.cursor() as cursor:
        connection.begin()
        cursor.executemany(
            "INSERT INTO outbox_events (event_key,event_type,aggregate_type,aggregate_id,"
            "aggregate_version,payload,status,attempts,available_at,created_at,updated_at) "
            "VALUES (%s,'REPORT_RESOLVED','REPORT',%s,1,%s,'PENDING',0,UTC_TIMESTAMP(6),"
            "UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", rows,
        )
        connection.commit()


def percentile(values, fraction):
    if not values:
        return None
    values = sorted(values)
    return round(values[int((len(values)-1) * fraction)], 1)


def soak(pairs):
    if SOAK_SECONDS < 60 or WORKERS < 2:
        raise AssertionError("SOAK_SECONDS >= 60 and SOAK_WORKERS >= 2 required")
    run_id = uuid.uuid4().hex[:10]
    prefix = f"multi-soak-{run_id}-"
    with database() as connection, connection.cursor() as cursor:
        cursor.execute("SELECT id,next_sequence FROM chat_rooms WHERE id IN (" +
                       ",".join(["%s"] * len(pairs)) + ")", [pair["roomId"] for pair in pairs])
        before = dict(cursor.fetchall())
    started = time.monotonic()
    deadline = started + SOAK_SECONDS
    records = []
    records_lock = threading.Lock()
    inserted_minutes = set()
    outbox_total = 0

    def worker(worker_id):
        local = []
        iteration = 0
        while time.monotonic() < deadline:
            pair = pairs[(worker_id + iteration) % len(pairs)]
            server = SERVERS[(worker_id + iteration) % 2]
            user = pair["owner"] if iteration % 2 == 0 else pair["requester"]
            key = str(uuid.uuid4())
            started_at = time.monotonic()
            try:
                status, response = send(server, pair, user, key, prefix + key)
                error = None if status == 201 else f"HTTP {status}: {str(response)[:120]}"
            except Exception as exception:
                status, response, error = 0, None, type(exception).__name__ + ": " + str(exception)[:120]
            local.append((int((started_at - started) // 60), pair["roomId"], server,
                          round((time.monotonic() - started_at) * 1000, 1), status,
                          response.get("sequence") if status == 201 else None, error))
            iteration += 1
            time.sleep(.15)
        with records_lock:
            records.extend(local)

    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        futures = [pool.submit(worker, index) for index in range(WORKERS)]
        while time.monotonic() < deadline:
            minute = int((time.monotonic() - started) // 60)
            if minute not in inserted_minutes:
                insert_outbox(pairs, run_id, minute)
                inserted_minutes.add(minute)
                outbox_total += 40
            time.sleep(1)
        for future in futures:
            future.result()

    windows = []
    for minute in range((SOAK_SECONDS + 59) // 60):
        window = [record for record in records if record[0] == minute]
        successful = [record for record in window if record[4] == 201]
        windows.append({"minute": minute + 1, "requests": len(window),
                        "success": len(successful), "failures": len(window)-len(successful),
                        "p95_ms": percentile([record[3] for record in successful], .95),
                        "rps": round(len(window)/60, 2)})
    successes = [record for record in records if record[4] == 201]
    failures = [record for record in records if record[4] != 201]
    with database() as connection, connection.cursor() as cursor:
        cursor.execute("SELECT room_id,COUNT(*),COUNT(DISTINCT sequence),MIN(sequence),MAX(sequence) "
                       "FROM messages WHERE body LIKE %s GROUP BY room_id", (prefix + "%",))
        db_rows = {room: (count, distinct, first, last) for room, count, distinct, first, last
                   in cursor.fetchall()}
        cursor.execute("SELECT id,next_sequence FROM chat_rooms WHERE id IN (" +
                       ",".join(["%s"] * len(pairs)) + ")", [pair["roomId"] for pair in pairs])
        after = dict(cursor.fetchall())
    issues = []
    if failures:
        issues.append(f"HTTP errors: {len(failures)}; first: {failures[0][6]}")
    if sum(value[0] for value in db_rows.values()) != len(successes):
        issues.append("DB count differs from successful HTTP sends")
    for pair in pairs:
        room = pair["roomId"]
        count, distinct, first, last = db_rows.get(room, (0, 0, None, None))
        if count != distinct or after[room]-before[room] != count or first != before[room] or last != after[room]-1:
            issues.append(f"Sequence inconsistency in room {room}")
    outbox_deadline = time.monotonic() + 90
    while time.monotonic() < outbox_deadline:
        with database() as connection, connection.cursor() as cursor:
            cursor.execute("SELECT status,COUNT(*) FROM outbox_events WHERE event_key LIKE %s GROUP BY status",
                           (f"perf-soak-{run_id}-%",))
            states = dict(cursor.fetchall())
            if states.get("FAILED"):
                raise AssertionError(f"Sustained load outbox failed: {states}")
            if states.get("DONE") == outbox_total:
                cursor.execute("SELECT COUNT(*),COUNT(DISTINCT n.event_id) FROM notifications n "
                               "JOIN outbox_events e ON e.id=n.event_id WHERE e.event_key LIKE %s",
                               (f"perf-soak-{run_id}-%",))
                notifications, unique_events = cursor.fetchone()
                if (notifications, unique_events) != (outbox_total, outbox_total):
                    raise AssertionError("Sustained load notifications duplicated or missing")
                break
        time.sleep(1)
    else:
        raise AssertionError("Sustained load outbox did not drain")
    return {"duration_s": SOAK_SECONDS, "workers": WORKERS, "requests": len(records),
            "success": len(successes), "failures": len(failures),
            "p95_ms": percentile([record[3] for record in successes], .95),
            "p99_ms": percentile([record[3] for record in successes], .99),
            "per_minute": windows, "db_messages": sum(value[0] for value in db_rows.values()),
            "outbox_events": outbox_total, "outbox_notifications": notifications,
            "issues": issues}


def main():
    result = {"environment": "2 Spring Boot / 1 MySQL / 1 Redis", "status": "running",
              "soak_target_seconds": SOAK_SECONDS, "soak_workers": WORKERS}
    client = docker.from_env()
    redis = container(client, "redis")
    backend_b = container(client, "backend-b")
    try:
        pairs = json.loads(FIXTURE.read_text())["pairs"]
        for server in SERVERS:
            wait_backend(server, pairs[0]["owner"])
        result["redis_outage"] = redis_outage(client, pairs[1])
        print("Redis outage and recovery passed", flush=True)
        result["server_restart"] = server_restart(client, pairs[2])
        print("backend-b restart and catch-up passed", flush=True)
        result["sustained_load"] = soak(pairs)
        if result["sustained_load"]["issues"]:
            raise AssertionError("; ".join(result["sustained_load"]["issues"]))
        print("sustained load passed", flush=True)
        result["status"] = "passed"
    except Exception as error:
        result["status"] = "failed"
        result["error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        redis.reload()
        if redis.status != "running":
            redis.start()
        backend_b.reload()
        if backend_b.status != "running":
            backend_b.start()
        OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"result: {OUTPUT}; status: {result['status']}", flush=True)


if __name__ == "__main__":
    main()
