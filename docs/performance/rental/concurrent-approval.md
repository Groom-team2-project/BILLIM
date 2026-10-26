# 겹치는 대여 요청 동시 승인 측정

## 문제

- 같은 물건·같은 기간에 대한 여러 요청 승인 시 중복 승인 여부와 경합 응답 시간 확인.

## 근거

- 측정 완료: 2026-10-26 22:59 KST.
- 서버 커밋: `d2c4fcb8861686a75c0b57c4e341e25a8d184881`.
- 환경: `billim-rental-perf`, MySQL 8.4, Java 21, k6, 단일 백엔드, 메모리 상한 1GiB, HTTP `127.0.0.1:18081`.
- 기존 목록 측정용 DB에 물건 20개·대기 요청 200개 추가. 기존 개발·실제 로그인 DB와 분리.
- 물건별 동일 소유자와 서로 다른 요청자 10명. 모든 요청의 기간 동일, 최초 version 0.
- 물건 하나당 승인 POST 10개를 `http.batch`로 동시 전송. 물건 20개를 순서대로 반복.
- k6 VU 1·iteration 1. HTTP 배치의 전체·호스트별 동시 연결 상한 10으로 설정. VU 10 구성과 구분.
- 요청별 다른 멱등 키 사용. 동일 요청 재전송 시험과 구분.
- 세션 쿠키·CSRF 검사 유지. 임시 세션 사용, 종료 후 폐기 및 원문 fixture 삭제.
- 정상 기대값: 물건별 HTTP 200 1건·409 `RENTAL_DATE_CONFLICT` 9건.

## 변경

- 서버·DB 구조·인증 로직 변경 없음.
- `performance/rental/concurrent-approval.js`에 동시 요청·응답 분류·물건별 검증 추가.
- `performance/rental/run_approval.py`에 데이터 생성·임시 인증·DB 후검증 추가.

## 결과

| 지표 | 결과 |
|---|---:|
| 전체 요청 | 200건 |
| 승인 성공 | 20건 |
| 예상 날짜 충돌 | 180건 |
| 예상 밖 응답 | 0건 |
| 물건별 검증 | 40/40 통과 |
| 전체 평균 | 62.32ms |
| 전체 p95 / p99 | 121.32ms / 350.59ms |
| 승인 평균 / p95 | 36.59ms / 48.42ms |
| 충돌 평균 / p95 | 65.18ms / 122.06ms |
| 전체 최대 응답 시간 | 368.24ms |
| 관측 요청률 | 112.02req/s |

- 최종 DB: 물건 20개 모두 `APPROVED` 1건·`REQUESTED` 9건.
- 승인 이력 20건, 약속 슬롯 40건, `RENTAL_APPROVED` outbox 이벤트 20건 확인.
- 중복 승인 및 중복 부수 효과 미발견. 요청자와 소유자의 회원 잠금·물건 잠금 아래 점유 재검사 동작 확인.
- k6 `http_req_failed`에는 정상적인 409도 실패로 집계되어 90% 표시. 서비스 장애율로 해석 금지. 판정은 오류 코드까지 확인한 `unexpected_responses=0` 및 물건별 검증 기준.
- 원본: [k6 결과](raw/approval.json), [DB 검증](raw/approval-verification.json).

## 재현

저장소 루트에서 실행. 최초 전용 회원·동네는 [목록 측정 실행](rental-list.md#재현)으로 준비 필요.

```bash
cd backend
./gradlew bootJar
cd ..
docker compose -f performance/rental/compose.yaml up -d --build --wait
python3 performance/rental/run_approval.py
docker compose -f performance/rental/compose.yaml stop
```

- 재실행마다 새 물건·요청 추가. 기존 거래를 되돌리지 않음. 반복 시 DB 전체 규모가 달라지는 한계.
- 로그·비밀 fixture는 Git 제외 경로 `performance/rental/local/` 사용. 공유용 JSON에는 세션 원문 없음.

## 한계

- 총 약 1.8초의 짧은 경합·정합성 시험. 준비 구간 별도 없음. 장기 부하·최대 처리량·안정성 시험 아님.
- 112.02req/s는 성공과 충돌 응답을 합한 관측치. 승인 처리량은 11.20건/s이며 최대 승인 처리량으로 일반화 불가.
- 동일 소유자의 승인 요청으로 회원 행 잠금 경합이 포함됨. 물건 잠금 비용만 분리 불가.
- DB 잠금 대기 시간·CPU·메모리 시계열 직접 측정 없음. 응답 시간 전체를 잠금 대기 시간으로 해석 불가.
- 소유자 여러 명·물건 여러 개의 동시 승인, 취소와 승인 간 경쟁, 날짜 경계·동일 키 재전송은 별도 시나리오.
- 다음 단계: 여러 소유자 분산 부하와 단일 소유자 경합 비교, DB 잠금 대기 계측, 개선 후 동일 데이터 조건 재측정.
