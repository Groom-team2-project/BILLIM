# 채팅 WebSocket 배포

> 버전 : v0.1.0\
> 수정일 : 2026.10.27

## 1. 현재 상태와 변경 목적

- Netlify 프론트와 `/api/*` 프록시 유지. 로그인 쿠키·OAuth 콜백 주소 유지.
- Netlify의 `/ws/chat`은 SPA HTML 200 반환. WebSocket 성공 응답 101 미반환.
- 코드 준비 상태. CloudFront 진입점 생성 및 원본 도달 확인 완료. 운영 코드 배포·연결권 활성화·인증된 WSS 검증 미완료.
- 생성 주소: `wss://d24eooh4xf18t4.cloudfront.net/ws/chat`. 배포 ID: `E1P3V3V2SU5FB5`.
- 별도 WSS 주소 미설정 시 기존 동일 출처 쿠키 연결 유지. 연결 실패 시 3초 REST 조회, 연결 정상 시 15초 보완 조회.

## 2. 연결 흐름

```text
브라우저 → Netlify /api/v1/chat/rooms/{roomId}/socket-tickets → 기존 EC2
           로그인 쿠키 + CSRF, 방 참여 권한 검증 → 연결권 반환
브라우저 → 별도 WSS 진입점 /ws/chat?roomId=...&ticket=... → 기존 EC2
           Origin·일회용 연결권·세션·참여 권한 확인 → 101 → 메시지 수신
```

- 연결권: 256비트 난수, 30초 TTL, Redis 해시 키, GETDEL 일회성 소비.
- 연결권 발급에 기존 로그인 세션 사용. API 명세 D_045의 연결권 계약 참조.
- 연결 후 기존 세션 ID에 수신 권한 연결. 로그아웃·만료·탈퇴·참여 권한 검사 유지.
- 대화 없는 연결은 서버가 20초마다 WebSocket Ping 전송. 프록시 유휴 종료 방지, 메시지 저장·sequence·DB 조회 미사용.
- 저장 메시지·대여·알림 계약과 DB 스키마 변경 없음.

## 3. AWS 진입점 준비

CloudFront는 WebSocket 지원. CloudFront 기본 `*.cloudfront.net` 도메인으로 브라우저 WSS 연결 가능. 사용자 소유 도메인은 선택 사항.

### 요금제와 비용

- 현재 선택: Pay-as-you-go. 월 고정요금 0달러이며 무료 보장·지출 상한을 의미하지 않음.
- 정액 Free·Pro의 VPC 원본 미지원. Business 정액제 선택 없이 종량제 사용.
- 공식 FAQ 기준 종량제 무료 구간: 계정 전체 월 데이터 전송 1TB·HTTP/HTTPS 요청 1,000만 건·Functions 200만 회. 초과분 및 무료 대상 외 기능 별도 과금 가능.
- WAF·Origin Shield·접근 로그 비활성. WAF 미사용에 따른 애플리케이션 공격·과부하 방어 한계 잔존. 연결권 인증은 WAF의 대체 기능이 아님.
- 크레딧의 CloudFront 적용 범위·잔액·만료일은 Billing의 Credits에서 별도 확인. 크레딧 소진 후 결제 차단 보장 없음.

### 원본 연결 방식

다음 중 현재 EC2 네트워크에 맞는 한 방식 확정 필요.

1. **VPC 원본**: CloudFront에서 EC2로 AWS 사설 경로 사용. 2026.05.01부터 WebSocket 지원. EC2 원본 선택 가능 여부, 서브넷·IGW·IPv4·보안 그룹 조건은 배포 담당 확인. 사설 HTTP는 EC2까지 TLS 암호화를 의미하지 않음. 현재 Netlify API가 공인 IP로 접근하므로 기존 라우팅·공인 접근 제거 전 API 배포 경로까지 검토 필요.
2. **공개 HTTPS 원본**: 팀 소유 원본 도메인과 유효 인증서 준비, EC2 리버스 프록시 HTTPS → `127.0.0.1:8080` 전달. CloudFront Origin Protocol Policy `HTTPS Only`. 자체 서명 인증서 사용 불가. 현재 HTTP 8080을 `HTTPS Only`로 지정하는 것만으로 TLS 생성 불가.

브라우저 WSS → CloudFront → 공개 HTTP 8080 구성은 원본 구간이 평문이므로 운영 적용 대상으로 사용하지 않음.

VPC 원본용 [CloudFormation 템플릿](templates/chat-wss-vpc.json) 제공. 기존 EC2 ARN·Private IP DNS name·포트 입력으로 VPC 원본과 채팅 전용 배포 생성. 캐시 없음, 필수 헤더·쿼리만 전달, 쿠키 미전달, `/ws/chat` 이외 경로 edge 차단, 접근 로그 비활성 구성. 사설 HTTP 원본용 템플릿이며 네트워크 조건·보안 그룹 확인 후 배포 담당자가 적용. AWS 자격 증명 부재로 실제 스택 생성·서비스 검증 미실행. 기존 EC2 보안 그룹·라우팅·백엔드 배포는 템플릿에서 변경하지 않음.

### CloudFront 동작 설정

| 항목 | 설정 |
| --- | --- |
| 용도 | 채팅 WSS 진입점. 프론트 이전 없음 |
| Origin path | 빈 값. `/ws`를 중복 부착하지 않음 |
| Viewer protocol policy | HTTPS Only |
| 경로 | `/ws/chat`, GET·HEAD 허용 |
| Cache policy | Managed-CachingDisabled |
| Origin request policy | WebSocket `Sec-WebSocket-*`, `Origin` 헤더와 `roomId`·`ticket` 쿼리 전달. 초기 구성은 Managed-AllViewer 사용 가능 |
| 기본 경로 | `/ws/chat` 이외 요청은 edge 함수 또는 원본 프록시에서 404 차단. 채팅 전용 배포로 다른 API·미디어 노출 방지 |
| 오류 응답 | API 오류를 SPA HTML로 바꾸는 규칙 금지 |
| 로그 | `ticket` 포함 쿼리·전체 URL 수집 금지. 쿼리를 제외할 수 없는 접근 로그 비활성화 |
| 원본 허용 | VPC 서비스 관리 보안 그룹 또는 공개 원본의 CloudFront 접근 제한 검토. Redis·관리 포트 공개 금지 |

CloudFront가 요청의 Origin을 프론트 `https://billim.netlify.app` 그대로 전달해야 함. 기본 배포 도메인을 Origin 허용 목록에 넣는 방식 아님.

## 4. 코드 배포와 설정 순서

1. 기본 비활성 상태의 백엔드·프론트 코드 배포.
2. AWS에서 원본 연결·경로 제한·쿼리 로그 제외·WebSocket 전달 설정 완료.
3. EC2 `.env.prod`에 다음 값 설정 후 백엔드 재시작. Redis 정상 동작 확인.

```dotenv
CHAT_SOCKET_TICKET_ENABLED=true
CHAT_SOCKET_ALLOWED_ORIGINS=https://billim.netlify.app
```

4. Netlify 환경 변수 설정 후 프론트 재빌드. Vite 환경 변수는 빌드 시 반영.

```dotenv
VITE_CHAT_WS_URL=wss://d24eooh4xf18t4.cloudfront.net/ws/chat
```

로컬·배포 미리보기 Origin을 무조건 허용하지 않음. 로컬 별도 WSS 시험은 `http://localhost:5173`를 필요한 환경에만 명시. `*`, 경로·끝 슬래시 포함 Origin, 평문 운영 Origin은 활성화 시 기동 거부.

## 5. 배포 검증

### 로컬 검증 결과 (2026.10.27)

| 검증 | 결과 |
| --- | --- |
| 백엔드 `./gradlew build` | 85개 테스트 클래스, 815건 통과, 실패·오류·건너뜀 0건 |
| 프론트 `npm test` | 59건 통과 |
| 프론트 `npm run lint`, `npm run build` | 통과 |
| 실제 Spring 보안 필터·WebSocket 연결 | 쿠키 없는 연결권 인증 성공, 재사용·연결권 없음 401, 미허용 Origin 403 |
| 실제 Redis | 해시 저장·TTL·일회성 소비·서로 다른 서비스의 동시 소비 차단 통과 |
| CloudFront 템플릿 | JSON 문법 및 경로 제한 함수 로컬 검증 통과. AWS 스택 생성·서비스 검증 미실행 |

Windows Java의 기존 루프백 연결 오류로 WSL Java 21 사용. Windows 마운트 경로에서 시작한 전체 검사는 느린 클래스 로딩으로 중단. 비밀 설정·빌드 산출물을 제외한 동일 백엔드 소스를 Linux 임시 경로에 복사해 전체 빌드 완료. 원본과 검증 소스의 `src`·`build.gradle` 일치 확인. 임시 경로의 빌드 시간 5분 48초이며 서비스 성능 측정값으로 사용하지 않음.

### 운영 적용 후 검증 항목

콘솔 수동 구성 후 진입점 확인 결과(2026.10.27):

- `/not-allowed`: CloudFront Function 생성 404·`Cache-Control: no-store` 확인. 경로 제한 함수 연결 확인.
- `/ws/chat?roomId=1&ticket=test`: 백엔드 JSON 401·`X-Request-Id` 확인. EC2 원본 도달 확인.
- 위 요청은 일반 HTTP GET 검사. 실제 WebSocket Upgrade·연결권 검증·헤더 및 쿼리 전달의 완전성은 미검증. 기존 보안 필터도 같은 401 반환 가능하므로 연결권 거부 테스트로 해석 금지.

새 코드 배포 및 환경 변수 적용 후 확인 항목:

- 두 로그인 세션 분리 후 채팅방 열기. ticket 발급 200 + Cache-Control no-store 확인.
- 개발자 도구 Network의 WS 요청: 별도 WSS 도메인, 101 Switching Protocols 확인. 원문 ticket 복사·공유 금지.
- 서로 다른 계정의 메시지 수신·중복 없음 확인. WS Frame과 REST 재조회로 받은 결과 구분.
- 동일 연결권 두 번째 연결 401, 만료 후 401, 다른 방 연결 401, 미허용 Origin 403 확인.
- 로그아웃 후 새 연결권 발급 차단 및 기존 소켓 수신 차단 확인.
- Redis 장애 시 발급 503, REST 조회·전송 유지 확인.
- 재연결마다 새 연결권 발급, 놓친 메시지 D_044 보완 조회 확인.
- 프론트·CloudFront·EC2 접근 로그·오류 추적 도구에 ticket·쿠키 원문 미기록 확인.

## 6. 복구

- Netlify `VITE_CHAT_WS_URL` 제거 후 재빌드 → 기존 동일 출처 시도와 REST 보완 조회로 복구.
- 백엔드 `CHAT_SOCKET_TICKET_ENABLED=false` 후 재시작 → 새 연결권 발급·소비 차단.
- 설정 비활성화는 이미 열린 소켓을 즉시 폐기하는 명령이 아님. 긴급 세션 폐기는 기존 로그아웃·세션 폐기 절차 사용.

## 7. 근거

- [AWS WebSocket 전달 헤더](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/distribution-working-with.websockets.html)
- [AWS CloudFront 종량제 무료 구간·정액제 비교](https://aws.amazon.com/cloudfront/faqs/)
- [AWS VPC 원본 WebSocket 지원 발표](https://aws.amazon.com/about-aws/whats-new/2026/05/amazon-cloudfront-websockets-vpc-origins/)
- [AWS VPC 원본 조건](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-vpc-origins.html)
- [CloudFront와 공개 원본 간 HTTPS](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/using-https-cloudfront-to-custom-origin.html)
- [Netlify WebSocket 프록시 미지원 안내](https://answers.netlify.com/t/does-netlify-support-websocket-proxying/11230/4)
