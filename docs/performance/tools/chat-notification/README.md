# 채팅·알림 성능 테스트

> 버전: v0.4.1
> 수정일: 2026.11.11

## 동네 소속 스키마 호환

`seed.py`·`seed-multi.py`는 격리 성능 DB의 `active_slot` 컬럼을 확인한 뒤 시드 생성. V23 이상은 저장 동네 슬롯 1을 명시하고, 이전 버전은 기존 INSERT 유지. 현재 동네는 기존과 같이 `members.active_community_id=910000` 사용. 메타데이터 조회 실패 시 과거 스키마로 추측하지 않고 중단. 기존 측정값 재산출·고부하 시험 미포함.

호환 판정 테스트: `python -m unittest discover -s docs/performance/tools/chat-notification/tests -v`

## 배포 서버의 짧은 조회 점검

`deploy-read-smoke.js`는 기본 사용자 1명·조회 24건·요청 종료 후 대기 1초로 동작. 정상 응답 기준 약 25~40초 예상, 느린 서버에서는 최대 2분에 중단. 한 번에 요청 1건, 평균 요청률은 초당 1건 미만. `RequestCount`로 4~60건 지정 가능. 로컬 고부하 스크립트와 별도 실행.

조회 대상은 채팅방 목록(20개), 채팅 미열람 수, 알림 목록(20개), 알림 미열람 수. `RoomId`를 지정하면 해당 방의 메시지 조회(20개)도 순환 대상에 추가. 총 요청 수는 그대로 유지. 실제 대화·거래가 있는 테스트 계정 권장. 비어 있는 계정이면 빈 목록 응답만 검증.

메시지 전송·읽음 처리·대여 상태 변경·WebSocket 연결·데이터 생성 미수행. 조회 요청에 따른 인증 세션 접근 시각 등 서버 내부 갱신은 발생 가능. 배포 서버에서 Docker를 실행하지 않고 테스트 PC에서 실행.

필요 조건: PowerShell 7, Docker Desktop, 테스트 계정의 유효한 `BILLIM_SESSION` 쿠키 값. 서비스에서 로그인한 뒤 브라우저 개발자 도구의 쿠키에서 값 확인. 실행기가 숨김 입력으로 받으므로 명령행에 세션 값 입력 불필요.

```powershell
pwsh -File docs/performance/tools/chat-notification/run-deploy-read-smoke.ps1 -BaseUrl https://서비스주소

# 메시지 조회 포함: 해당 계정이 참여하는 방 ID 사용
pwsh -File docs/performance/tools/chat-notification/run-deploy-read-smoke.ps1 -BaseUrl https://서비스주소 -RoomId 123
```

`BaseUrl`은 `/api/v1` 경로 없이 출처만 지정. 원격 서버는 HTTPS 필수. Netlify 등 기존 서비스 프록시 주소 사용 권장. 서명 검증을 강제하는 백엔드에 직접 접속하는 경우 `-DirectBackend`를 지정하고 기존 `../auth/nf_sign.py`로 발급한 `x-nf-sign` 값을 추가 입력. 서명 검증 설정 변경 불필요.

`billim-deploy-read-날짜시간` 컨테이너를 백그라운드로 실행하고 추적하지 않음. HTTP 오류·인증 실패·요청 제한·잘못된 JSON 등 첫 응답 오류에서 즉시 중단. 요청 제한을 우회하거나 재시도하지 않음. 리다이렉트 미추적. 세션·응답 본문은 출력하지 않음.

종료 후 컨테이너 종료 코드 0과 `local/billim-deploy-read-날짜시간/summary.json` 확인. `endpoint_*_ms`에 경로별 평균·중앙값·p95·최댓값, `http_reqs`에 완료 요청 수, `app_failures`에 응답 검증 실패율 기록. 시간 초과 등으로 일부만 수행하면 요청 수 확인 필요. `manifest.json`에 실행 조건 기록. 같은 디렉터리의 `session.json`은 세션 원문 포함하므로 결과 확인 후 삭제. `local/` 전체는 Git 제외.

24건은 배포 후 상태와 지연을 가볍게 확인하는 표본. 엔드포인트별 요청은 약 4~6건이므로 p95를 안정적인 운영 성능 수치나 최대 처리량으로 해석하지 않음. 배포 전후 비교는 같은 계정·방·경로·요청 수로 수행. 대규모 회귀·포화 지점 검증은 기존 로컬 도구 사용.

검증: `node test-deploy-read-smoke.cjs`에서 요청 경로·상한·오류 중단 검사. k6 `inspect`는 네트워크 비활성 컨테이너에서 통과. 실제 배포 서버 호출 미실행.

## 워밍업 통제 목록 비교

[메시지·대여·알림 포함 비교 결과](../../박선우-채팅목록-실데이터비교.md)와 [공유 원자료](../../raw/chat-rich-controlled-20261103/result.json) 참고.

- `compose.rich.yaml`의 `billim-perf-rich` 전용 DB·포트 18083 사용. 기존 개발·운영 데이터 미사용.
- `rich-benchmark.py`로 기준 데이터 생성 및 버전별 최초 측정. `RICH_VERSION=baseline`은 반드시 변경 전 백엔드 소스로, `optimized`는 개선 소스로 빌드. 현재 개선 소스를 baseline 이름으로 다시 빌드하면 비교 무효.
- 컨테이너 내부 `/scripts`에 이 디렉터리, `/backend`에 해당 버전 백엔드 디렉터리 마운트. Docker 소켓 필요. `HOST_BACKEND`는 Docker 데몬에서 접근할 호스트 백엔드 경로. Windows Docker Desktop 예: `/run/desktop/mnt/host/c/Users/ParkSunWoo/Bootcamp/BILLIM/backend`.
- 최초 실행 환경: `COMPOSE_PROJECT_NAME=billim-perf-rich`, `PERF_BASE_URL=http://backend:8080`, `PERF_PAIRS=50`, `PERF_SHARED_OWNER=true`, `PERF_NOTIFICATIONS_PER_MEMBER=100`, `PERF_FIXTURE_NAME=fixtures-rich.json`, `RUNNER_NAME`은 실제 실행 컨테이너 이름. `Dockerfile.rich-runner`로 실행 이미지 생성.
- 통제 재측정은 `billim-perf-rich:baseline`, `billim-perf-rich:optimized` 이미지와 기존 `local/fixtures-rich.json` 및 DB 필요. 최초 측정과 동시에 실행 금지.
- `rich-controlled.py`를 같은 실행 이미지에서 실행. Docker 소켓과 `/scripts` 마운트, `billim-perf-rich_default` 네트워크, 실제 `RUNNER_NAME`, 새 `CONTROLLED_LABEL` 지정. 기존 결과 이름은 덮어쓰지 않음. 예: `--entrypoint python3 billim-perf-rich-runner /scripts/rich-controlled.py`.
- 통제 측정은 MySQL·백엔드를 매회 재시작하므로 해당 격리 프로젝트의 다른 시험과 동시 사용 금지. `compose.rich.controlled.yaml`로 버퍼 풀 자동 복원 비활성. 측정 중 메시지·대여·알림 변경 미수행.
- 완료 여부는 실행 컨테이너 종료 코드와 `local/{CONTROLLED_LABEL}-result.json`의 `status`로 확인. `local/`에는 세션 원문이 있으므로 전체 디렉터리를 공유하지 않음.
- 과거 버전 비교는 `CONTROLLED_BASELINE`·`CONTROLLED_OPTIMIZED` 이미지 태그 지정 가능. `HOST_OLD_BACKEND` 및 `/oldbackend` 마운트로 과거 소스 빌드 가능.
- 쿼리 진단은 `CONTROLLED_ORDER`에 이미지 태그를 쉼표로 나열하고 `DIAGNOSTIC=true` 지정. 측정 구간 쿼리별 누적 비용과 CPU 카운터 추가 저장. `HOST_FIXED_BACKEND`와 `/fixedbackend` 마운트를 지정하면 전체 빌드·테스트 통과 후 `fixed` 이미지 생성. 측정 실행 중 소스 변경 금지.

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
