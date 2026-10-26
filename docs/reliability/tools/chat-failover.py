"""격리된 로컬 Compose 채팅 스택의 장애 복구 경로 확인."""

import argparse
import json
import subprocess
import sys
import time
import uuid
from pathlib import Path


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
PERF = ROOT / "docs" / "performance" / "tools" / "chat-notification"
LOCAL = PERF / "local"
sys.path.insert(0, str(LOCAL / "deps"))
import requests  # noqa: E402
import websocket  # noqa: E402


CONTAINERS = {
    "redis": "billim-perf-multi-redis-1",
    "mysql": "billim-perf-multi-mysql-1",
    "backend-b": "billim-perf-multi-backend-b-1",
}
A = "http://127.0.0.1:18080"
B = "http://127.0.0.1:18081"
ROOM_PATH = "/api/v1/chat/rooms/{room_id}/messages"


def docker(*args):
    return subprocess.run(["docker", *args], text=True, capture_output=True, check=True).stdout.strip()


def assert_isolated():
    for name in CONTAINERS.values():
        project = docker("inspect", "--format", "{{index .Config.Labels \"com.docker.compose.project\"}}", name)
        if project != "billim-perf-multi":
            raise RuntimeError(f"격리 Compose 컨테이너가 아님: {name}")


def wait_ready(base, user, timeout=60):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            response = requests.get(
                base + "/api/v1/auth/csrf",
                headers={"Cookie": f"BILLIM_SESSION={user['token']}"},
                timeout=2,
            )
            if response.status_code == 200:
                return
        except requests.RequestException:
            pass
        time.sleep(1)
    raise RuntimeError(f"백엔드 준비 시간 초과: {base}")


def wait_redis_healthy(timeout=30):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if docker("inspect", "--format", "{{.State.Health.Status}}", CONTAINERS["redis"]) == "healthy":
            return
        time.sleep(1)
    raise RuntimeError("Redis 준비 시간 초과")


def wait_subscribers(expected=2, timeout=15):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        output = docker("exec", CONTAINERS["redis"], "redis-cli", "PUBSUB", "NUMSUB", "billim:chat:committed")
        count = int(output.splitlines()[-1])
        if count >= expected:
            return count
        time.sleep(1)
    raise RuntimeError("Redis 구독 복구 시간 초과")


def headers(user):
    return {
        "Cookie": f"BILLIM_SESSION={user['token']}",
        "X-CSRF-TOKEN": user["csrf"],
    }


def send(base, room_id, user, marker, timeout=45):
    client_id = str(uuid.uuid4())
    started = time.monotonic()
    try:
        response = requests.post(
            base + ROOM_PATH.format(room_id=room_id),
            headers={**headers(user), "Content-Type": "application/json"},
            json={"clientMessageId": client_id, "body": marker},
            timeout=timeout,
        )
        return client_id, response.status_code, round((time.monotonic() - started) * 1000), None
    except requests.RequestException as exc:
        return client_id, "request_error", round((time.monotonic() - started) * 1000), type(exc).__name__


def query(base, room_id, user, after=None):
    response = requests.get(
        base + ROOM_PATH.format(room_id=room_id),
        headers=headers(user),
        params={"afterSequence": after, "limit": 50} if after is not None else {"limit": 50},
        timeout=10,
    )
    response.raise_for_status()
    return response.json()


def latest_sequence(base, room_id, user):
    return max((item["sequence"] for item in query(base, room_id, user)["items"]), default=0)


def connect(base, room_id, user):
    return websocket.create_connection(
        base.replace("http", "ws") + f"/ws/chat?roomId={room_id}",
        header=[f"Cookie: BILLIM_SESSION={user['token']}"],
        timeout=5,
        http_proxy_host=None,
    )


def receive_id(socket, client_id, timeout):
    start = time.monotonic()
    end = start + timeout
    while time.monotonic() < end:
        socket.settimeout(min(2, max(0.1, end - time.monotonic())))
        try:
            payload = json.loads(socket.recv())
            if payload.get("clientMessageId") == client_id:
                return round((time.monotonic() - start) * 1000)
        except websocket.WebSocketTimeoutException:
            continue
    return None


def test_redis(room_id, user):
    socket = connect(B, room_id, user)
    try:
        docker("stop", CONTAINERS["redis"])
        stopped_at = time.monotonic()
        messages = []
        for index, offset in enumerate((0, 10, 20, 30)):
            time.sleep(max(0, stopped_at + offset - time.monotonic()))
            before = latest_sequence(A, room_id, user)
            client_id, status, http_ms, _ = send(A, room_id, user, f"[FAULT TEST] redis unavailable {index}")
            receive_ms = receive_id(socket, client_id, 12) if status == 201 else None
            persisted = any(item["clientMessageId"] == client_id for item in query(A, room_id, user, before)["items"])
            messages.append({"send_status": status, "http_ms": http_ms,
                             "socket_receive_after_http_ms": receive_ms,
                             "send_to_socket_ms": http_ms + receive_ms if receive_ms is not None else None,
                             "persisted": persisted})
        result = {"redis_unavailable_s": round(time.monotonic() - stopped_at, 2), "messages": messages}
    finally:
        docker("start", CONTAINERS["redis"])
        wait_redis_healthy()
        socket.close()

    # 구독 재개 여부는 별도 메시지로 확인한다. 실시간 기준은 1초로 기록만 남긴다.
    result["restored_subscribers"] = wait_subscribers()
    socket = connect(B, room_id, user)
    try:
        client_id, status, http_ms, _ = send(A, room_id, user, "[FAULT TEST] redis restored")
        receive_ms = receive_id(socket, client_id, 8) if status == 201 else None
        result["restored_send_status"] = status
        result["restored_http_ms"] = http_ms
        result["restored_receive_after_http_ms"] = receive_ms
        result["restored_send_to_socket_ms"] = http_ms + receive_ms if receive_ms is not None else None
    finally:
        socket.close()
    return result


def test_backend_restart(room_id, user):
    socket = connect(B, room_id, user)
    before = latest_sequence(A, room_id, user)
    try:
        docker("stop", CONTAINERS["backend-b"])
        socket.settimeout(3)
        try:
            closed_on_stop = not bool(socket.recv())
        except (websocket.WebSocketConnectionClosedException, OSError):
            closed_on_stop = True
        except websocket.WebSocketTimeoutException:
            closed_on_stop = False
        client_id, status, http_ms, _ = send(A, room_id, user, "[FAULT TEST] receiver backend down")
    finally:
        socket.close()
        docker("start", CONTAINERS["backend-b"])
        wait_ready(B, user)

    socket = connect(B, room_id, user)
    try:
        # 재연결 WebSocket은 현재 순번에서 시작한다. 누락분은 REST로 복구되는지 확인한다.
        socket_replayed = receive_id(socket, client_id, 2) is not None
        page = query(B, room_id, user, before)
        recovered = any(item["clientMessageId"] == client_id for item in page["items"])
    finally:
        socket.close()
    return {"socket_closed_on_stop": closed_on_stop, "send_status": status, "http_ms": http_ms,
            "websocket_replayed": socket_replayed,
            "api_recovered": recovered}


def test_mysql(room_id, user):
    before = latest_sequence(A, room_id, user)
    try:
        docker("stop", CONTAINERS["mysql"])
        client_id, status, http_ms, error = send(A, room_id, user, "[FAULT TEST] mysql unavailable", timeout=60)
    finally:
        docker("start", CONTAINERS["mysql"])
        wait_ready(A, user)
    persisted = any(item["clientMessageId"] == client_id for item in query(A, room_id, user, before)["items"])
    return {"send_status": status, "http_ms": http_ms, "request_error_type": error,
            "persisted_after_recovery": persisted}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("scenario", choices=["redis", "backend-restart", "mysql"])
    args = parser.parse_args()
    assert_isolated()
    pair = json.loads((LOCAL / "fixtures.json").read_text(encoding="utf-8"))["pairs"][0]
    room_id, user = pair["roomId"], pair["requester"]
    wait_ready(A, user)
    wait_ready(B, user)
    cases = {"redis": test_redis, "backend-restart": test_backend_restart, "mysql": test_mysql}
    detail = cases[args.scenario](room_id, user)
    if args.scenario == "redis":
        passed = all(message["send_status"] == 201 and message["persisted"]
                     and message["send_to_socket_ms"] is not None for message in detail["messages"])
        passed = passed and detail["restored_subscribers"] >= 2 \
            and detail["restored_send_status"] == 201 \
            and detail["restored_send_to_socket_ms"] is not None
    elif args.scenario == "backend-restart":
        passed = detail["socket_closed_on_stop"] and detail["send_status"] == 201 \
            and detail["api_recovered"]
    else:
        passed = detail["send_status"] != 201 and not detail["persisted_after_recovery"]
    result = {"scenario": args.scenario, "roomId": room_id, "passed": passed, "result": detail}
    out = LOCAL / f"failover-{args.scenario}-20261026.json"
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False))
    raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
