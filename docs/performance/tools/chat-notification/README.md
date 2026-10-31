# 채팅·알림 로컬 성능 테스트

후속 목록·유휴 연결·전송·outbox 비교는 [2026.10.31 후속 측정](../../박선우-채팅알림-후속측정.md)에 조건과 결과를 기록했다. `PERF_SHARED_OWNER=true`와 `PERF_FIXTURE_NAME=fixtures-followup.json`을 사용하면 한 회원이 여러 채팅방을 가진 독립 fixture를 생성한다. 이 시험은 Compose 프로젝트를 `billim-perf-followup`으로 지정해 기존 `billim-perf` 볼륨과 분리한다. `measure-room-list.py`, `count-room-list-sql.py`, `measure-idle-queries.py`, `measure-send.py`가 각 경로를 측정한다. outbox 50/100 비교는 `Dockerfile.outbox-benchmark`와 `run-outbox-benchmark.sh`로 만든 Docker 컨테이너 안에서 실행하며, 컨테이너 종료 코드로 완료 여부를 확인한다.

멀티 인스턴스 동작 검증은 `compose.multi.correctness.yaml`을 `billim-perf-multi-20261031` 프로젝트로 띄우고 `fixtures-multi.json`을 시드한 뒤, `benchmark` 서비스를 실행한다. 기존 `compose.multi.yaml`의 k6 성능 실험과 별도 구성이다. Docker Desktop에서 `billim-perf-multi-20261031-benchmark`의 종료 코드 0과 `local/multi-instance-result-20261031.json`의 `status: passed`를 함께 확인한다. 이 결과 파일에는 세션 원문이 없다.

Redis 장애·서버 재시작·지속 부하는 같은 프로젝트의 `resilience` 서비스로 실행한다. 이 컨테이너는 **해당 프로젝트의 Redis와 `backend-b`만** 잠시 중단·재시작하고, 최종적으로 두 컨테이너를 실행 상태로 되돌린다. `SOAK_SECONDS=600`, `SOAK_WORKERS=24`가 기본값이다. Docker Desktop에서 `billim-perf-multi-20261031-resilience`의 종료 코드와 `local/multi-instance-resilience-20261031.json`을 확인한다. 시험 중에는 이 Compose 프로젝트를 다른 테스트에 재사용하지 않는다.

```powershell
$env:COMPOSE_PROJECT_NAME = 'billim-perf-multi-20261031'
$env:PERF_FIXTURE_NAME = 'fixtures-multi.json'
$env:PERF_PAIRS = '8'
$env:PERF_NOTIFICATIONS_PER_MEMBER = '0'
docker compose -p billim-perf-multi-20261031 -f docs/performance/tools/chat-notification/compose.multi.correctness.yaml up -d --build mysql redis backend-a backend-b
python docs/performance/tools/chat-notification/seed.py
docker compose -p billim-perf-multi-20261031 -f docs/performance/tools/chat-notification/compose.multi.correctness.yaml run -d --no-deps --name billim-perf-multi-20261031-benchmark benchmark
docker wait billim-perf-multi-20261031-benchmark
docker compose -p billim-perf-multi-20261031 -f docs/performance/tools/chat-notification/compose.multi.correctness.yaml run -d --no-deps --name billim-perf-multi-20261031-resilience resilience
```

백엔드가 아직 기동 중이라 시드의 CSRF 확인만 실패하면 DB와 세션 fixture는 이미 생성된 상태다. 다시 시드하지 말고 `python docs/performance/tools/chat-notification/seed.py --refresh-csrf`를 실행한다. 동일한 fixture 이름과 컨테이너 이름으로 재실행하려면 먼저 격리 프로젝트의 이전 시험 데이터를 정리하거나 새 이름·프로젝트를 지정한다.

배포 환경과 개발 DB를 사용하지 않는 별도 `billim-perf` Compose 프로젝트입니다. 테스트 계정의 세션은 이 DB에만 넣으며, 실제 카카오 키는 필요하지 않습니다. `docs/performance/tools/chat-notification/local/`에는 테스트 세션 원문이 있으므로 Git에서 제외합니다. 스크립트와 컨테이너는 본인 PC에서만 실행하세요.

## 준비 (PowerShell, 저장소 루트)

1. Docker Desktop을 켭니다.
2. 현재 소스의 백엔드 JAR을 만듭니다. Windows에서 Gradle loopback 오류가 나면 WSL을 사용합니다.

   ```powershell
   wsl -e sh -lc 'cd backend && ./gradlew bootJar --no-daemon'
   ```

3. 독립 스택을 띄웁니다. `http://localhost:18080/api/v1/auth/csrf`가 HTTP 200을 반환할 때까지 기다립니다.

   ```powershell
   docker compose -f docs/performance/tools/chat-notification/compose.yaml up -d --build mysql backend
   ```

4. Python 3으로 테스트 데이터와 세션을 만듭니다. **기존 DB에서 실행하지 마세요.**

   ```powershell
   python docs/performance/tools/chat-notification/seed.py
   ```

   DB 저장 후 CSRF 확인만 실패해도 `docs/performance/tools/chat-notification/local/fixtures.json`에는 생성한 세션 원문이 남습니다. 백엔드가 준비된 뒤 아래 명령으로 확인을 다시 실행하세요. DB 초기화나 전체 시드 재실행은 필요하지 않습니다.

   ```powershell
   python docs/performance/tools/chat-notification/seed.py --refresh-csrf
   ```

## 실행

먼저 1명, 5초로 접속과 응답 상태를 확인합니다.

```powershell
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=chat-send -e VUS=1 -e DURATION=5s k6 run /scripts/chat-notification.js
```

이후 아래 CASE별로 실행합니다. `VUS=4`, `DURATION=30s` 정도부터 시작하고, 필요하면 8명으로 올립니다. k6 결과의 `http_req_duration` p95, `http_reqs`, `app_failures`, WebSocket의 `chat_delivery_ms`를 기록합니다. `notification-read`는 80회만 실행하며 `DURATION`은 사용하지 않습니다.

```powershell
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=chat-send -e VUS=4 -e DURATION=30s k6 run /scripts/chat-notification.js
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=chat-list -e VUS=4 -e DURATION=30s k6 run /scripts/chat-notification.js
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=chat-inbox -e VUS=4 -e DURATION=30s k6 run /scripts/chat-notification.js
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=chat-websocket -e VUS=4 -e DURATION=30s k6 run /scripts/chat-notification.js
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=notification-list -e VUS=4 -e DURATION=30s k6 run /scripts/chat-notification.js
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=notification-read -e VUS=4 k6 run /scripts/chat-notification.js
```

`chat-send`와 `chat-websocket`은 DB에 메시지가 쌓이고 `notification-read`는 알림 상태를 바꿉니다. 비교 실험마다 새 DB로 초기화하고 동일한 순서로 측정하세요. `chat-list`는 메시지 20개 이상이 쌓인 후 실행해야 실제 목록 경로를 측정합니다.

고부하 시 `VUS`를 16 → 32 → 64 순서로 올리되, 오류율 1% 이상·HTTP p95 2초 이상·백엔드 메모리 1GiB 근접 시 멈춥니다. `-e HOT_ROOM=true`를 추가하면 모든 가상 사용자가 한 방에 요청해 순번 할당 잠금 경합을 측정합니다. 기존 8개 방 분산 결과와 구분해서 기록하세요.

WebSocket 시나리오는 연결 직후 전송을 기본으로 합니다. `-e WS_SEND_DELAY_MS=50` 또는 `100`을 추가하면 연결 후 지정한 시간만큼 기다렸다가 전송합니다. `chat_delivery_failures`는 HTTP 201에 성공한 메시지 중 실시간 미수신 비율입니다. HTTP 전송 실패는 `app_failures`, 소켓 연결 실패는 `chat_websocket_connection_failures`로 확인하세요. 이 시나리오는 매 반복마다 소켓을 새로 여므로 장기 연결 시험과 구분하세요.

실제 채팅 화면에 가까운 유지 연결 진단은 `-e CASE=chat-websocket-stable -e VUS=64`로 실행합니다. VU마다 소켓 한 개를 유지하고 메시지 10건을 보냅니다. `chat_first_delivery_failures`와 `chat_later_delivery_failures`를 비교해 연결 직후에만 놓치는지 확인합니다. 연결 실패나 HTTP 전송 실패로 10건을 채우지 못한 반복은 `chat_websocket_incomplete_iterations`에 기록합니다. 전달 실패율의 분모에는 HTTP 201인 메시지만 포함합니다.

### 서로 다른 사용자·채팅방의 1:1 대화

독립 DB를 초기화한 뒤 64개 방과 128개 계정을 만듭니다. 기존 `docs/performance/tools/chat-notification/local/fixtures.json`은 초기화 후에도 남으므로, 새 시드를 만들기 전에 별도로 보관하거나 삭제합니다. 이 데이터 구성은 기존 알림 부하 시드와 구분됩니다.

```powershell
$env:PERF_PAIRS = '64'
$env:PERF_NOTIFICATIONS_PER_MEMBER = '1'
python docs/performance/tools/chat-notification/seed.py
Remove-Item Env:PERF_PAIRS
Remove-Item Env:PERF_NOTIFICATIONS_PER_MEMBER
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=chat-realistic -e VUS=64 k6 run --summary-trend-stats 'avg,min,med,max,p(90),p(95),p(99)' /scripts/chat-notification.js
```

VU마다 고유 방 한 개를 사용하며, 방별 소유자 세션이 2초 간격으로 5건을 보내고 요청자 세션이 유지 중인 WebSocket으로 받습니다. `VUS`는 생성한 방 수 이하여야 합니다. `chat_delivery_ms`는 상대 계정의 실시간 수신까지의 시간입니다. 실제 브라우저 표시 시간과 인터넷 구간은 포함하지 않습니다.

두 참여자가 모두 WebSocket에 접속해 양방향으로 보내고 받는 경우는 `chat-paired`를 사용합니다. **방 하나당 2 VU**가 필요하므로 `VUS`는 짝수이며 생성한 방 수의 두 배 이하여야 합니다. 각 VU는 연결 3초 뒤부터 2초 간격으로 5건을 보내고 상대방의 5건을 확인합니다. `chat_delivery_ms`는 상대방이 보낸 메시지를 내 소켓이 수신할 때까지의 시간이고, `chat_peer_receipt_failures`는 상대방 메시지 5건 중 받지 못한 비율입니다. 상대방의 HTTP 전송이 실패해도 미수신으로 집계되므로 원인 분석 시 `app_failures`를 함께 확인합니다.

```powershell
docker compose -f docs/performance/tools/chat-notification/compose.yaml run --rm -e CASE=chat-paired -e VUS=128 k6 run --summary-trend-stats 'avg,min,med,max,p(90),p(95),p(99)' /scripts/chat-notification.js
```

128개 방·256 VU 조건은 독립 DB를 다시 초기화하고 `PERF_PAIRS='128'`, `PERF_NOTIFICATIONS_PER_MEMBER='0'`으로 시드한 뒤 `VUS=256`으로 실행합니다. 기존 64개 방 시드로 256 VU를 실행하면 방을 중복 사용하게 되므로 `chat-paired`는 이를 오류로 처리합니다.

### 알림 outbox 처리

HTTP 측정을 마친 뒤 worker를 활성화해 합성 신고 처리 사건 100건이 실제 알림으로 변환될 때까지의 시간과 처리 지연을 측정합니다. 이 테스트는 도메인 사건 발행 API 전체를 재현하지 않습니다.

```powershell
$env:PERF_OUTBOX_ENABLED = 'true'
docker compose -f docs/performance/tools/chat-notification/compose.yaml up -d --force-recreate backend
python docs/performance/tools/chat-notification/outbox.py 100
Remove-Item Env:PERF_OUTBOX_ENABLED
docker compose -f docs/performance/tools/chat-notification/compose.yaml up -d --force-recreate backend
```

더 큰 알림 적체는 준비된 worker에서 `python docs/performance/tools/chat-notification/outbox.py 500`으로 측정할 수 있습니다. 결과의 `lag_p95_ms`는 사건 생성부터 처리 완료까지의 지연이며, 1초 폴링 대기와 배치 처리 시간을 모두 포함합니다.

## 종료·초기화

```powershell
docker compose -f docs/performance/tools/chat-notification/compose.yaml down -v
Remove-Item -LiteralPath docs/performance/tools/chat-notification/local -Recurse -Force
```

`down -v`는 **billim-perf 프로젝트의 독립 DB 볼륨만** 삭제합니다. 다시 실행할 때는 준비 3~4단계부터 시작합니다.

## 범위

- HTTP: 채팅 전송·메시지 목록·방 목록·미열람 수, 알림 목록·미열람 수·개별 읽음.
- WebSocket: 연결 후 메시지 전송과 같은 방 실시간 수신까지의 시간.
- 테스트는 1개 백엔드 인스턴스, 한 PC의 Docker 환경 기준입니다. 실제 카카오 로그인, 대여 사건 생성부터 알림 소비까지의 전체 흐름과 다중 인스턴스 전달 성능은 포함하지 않습니다.
- `/actuator/prometheus`는 현재 보안 설정에서 인증이 필요한 경로입니다. 배포 서버의 Prometheus 수집을 위해 인증이나 네트워크 정책을 바꾸는 작업은 팀 합의 후 진행하세요.
