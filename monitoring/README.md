# 모니터링

운영 앱 서버와 2차 재측정 복제 환경의 지표를 한 곳에서 수집·표시. 모니터링 전용 EC2에서 실행. 구성 이유는 [배포 전략](../docs/deploy/배포%20전략.md) §6.

| 구성 | 역할 |
| --- | --- |
| Prometheus | 앱 서버 관리 포트(9090) `/actuator/prometheus` 15초 간격 수집, 30일 보관 |
| Grafana | 대시보드 `BILLIM 서버 개요` · 운영(`env=prod`) 알림 4종 → 디스코드 |

## 대시보드

서버 상태 · 서버별 요청 수 · 서버별 p95 · 5xx 비율 · 커넥션 풀 사용 중·대기 · JVM 힙 · CPU · 엔드포인트별 p95 상위 10. 상단 `환경`으로 운영·복제 환경 구분.

p95는 운영 프로필의 `http.server.requests` 히스토그램 전제(`application.yml`). 다른 프로필은 p95 패널이 비어 있음.

## 알림

| 규칙 | 조건 |
| --- | --- |
| 서버 수집 실패 | `up` 0 또는 대상 없음, 1분 지속 |
| 5xx 비율 | 1% 초과, 5분 지속 |
| p95 응답시간 | 500ms 초과, 5분 지속 |
| JVM 힙 사용률 | 90% 초과, 5분 지속 |

복제 환경(`env=replica`)은 부하 측정 중 임계값을 일부러 넘기므로 알림 제외.

## 실행

```bash
cp .env.example .env                                   # 관리자 비밀번호·디스코드 웹후크 입력
cp prometheus/targets/app.json.example prometheus/targets/app.json   # 실제 사설 IP로 수정, 쓰지 않는 대상은 삭제
docker compose up -d
```

- 앱 서버는 `.env.prod`에 `MANAGEMENT_ADDRESS=0.0.0.0` 지정 후 재기동. 접근 제한은 보안 그룹 — 9090 인바운드는 모니터링 서버 보안 그룹만 허용
- 대상 추가·교체는 `targets/app.json`만 수정 — 30초 내 반영, 재시작 불필요
- 외부 포트 미개방

## 접속 (팀원)

외부에 열지 않아 각자 PC에서 Session Manager 포트 포워딩으로 접속.

**준비 (1회)**

| 항목 | 방법 |
| --- | --- |
| AWS 권한 | `ssm:StartSession`(모니터링 인스턴스·`AWS-StartPortForwardingSession` 문서로 제한) · `ssmmessages:OpenDataChannel` · `ec2:DescribeInstances`. 관리자 그룹이면 이미 있음 |
| 도구 | `brew install awscli` · `brew install --cask session-manager-plugin` |
| 자격 증명 | IAM 콘솔에서 본인 액세스 키 발급 → `aws configure` (리전 `ap-northeast-2`) |

**접속**

```bash
# 모니터링 서버 ID 조회
M=$(aws ec2 describe-instances --region ap-northeast-2 \
  --filters Name=tag:Name,Values=billim-monitoring Name=instance-state-name,Values=running \
  --query 'Reservations[0].Instances[0].InstanceId' --output text)
echo $M   # None이면 EC2 콘솔에서 billim-monitoring의 ID를 찾아 M=<인스턴스 ID>로 지정

# Grafana — 브라우저 http://localhost:3000 (창을 닫으면 접속 종료)
aws ssm start-session --region ap-northeast-2 --target $M \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["3000"]}'

# Prometheus — 쿼리를 직접 볼 때만. 브라우저 http://localhost:9091
aws ssm start-session --region ap-northeast-2 --target $M \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["9091"],"localPortNumber":["9091"]}'
```

- 로컬 3000번을 이미 쓰고 있으면(프론트 개발 서버 등) `localPortNumber`를 `3300`처럼 바꾸고 그 포트로 접속
- 로그인 없이 보기 전용으로 접속. 대시보드·알림 수정은 관리자 계정 필요
- 대시보드는 `Dashboards → BILLIM → BILLIM 서버 개요`. 상단 `환경`으로 운영(`prod`)·재측정 복제 환경(`replica`) 구분
