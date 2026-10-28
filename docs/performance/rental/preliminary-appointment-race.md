> 예비 측정 기록. 공통 양식의 3회 반복 통계와 구분.

# 약속 수락·철회 동시 변경 검증

## 문제

- 같은 미확정 약속에 상대방의 수락과 제안자의 철회가 동시에 발생할 때 단일 상태 전이·슬롯 버전·확정 참조의 정합성 확인.

## 근거

- 완료: 2026-10-28 19:52 KST.
- 서버 커밋: `06f902cedc40e906b1b51e67e7b12691c77eae7c`.
- 기존 전용 `billim-rental-perf` DB·단일 백엔드 사용. MySQL 8.4, Java 21, HTTP `127.0.0.1:18081`, 백엔드 메모리 상한 1GiB.
- 기존 개발·실제 로그인 DB와 분리, 전용 가상 회원 2명의 임시 세션 사용.
- 새 물건 20개·승인 대여 20개·슬롯 40개·전달 약속 제안 20개 생성.
- 전달 슬롯 초기 version 1, 제안 상태 PROPOSED, 확정 참조 없음. 반납 슬롯은 version 0 유지.
- 약속은 KST 기준 이틀 뒤 전달일 정오로 설정. 제안자는 소유자, 수락자는 대여자.
- 약속마다 다음 2개 요청을 HTTP batch로 동시 전송. 두 요청 모두 `expectedVersion=1`, 각기 다른 멱등 키 사용.
  - 대여자: `POST /api/v1/appointments/{proposalId}/accept`.
  - 소유자: `POST /api/v1/appointments/{proposalId}/withdraw`.
- 총 20개 약속을 순차 반복, 40건 요청. 배치 안 요청 순서는 매회 교대. 요청 순서가 서버 처리 순서를 보장하지 않음.
- k6 VU 1·iteration 1·동시 HTTP 연결 상한 2. 장시간 VU 부하 시나리오와 구분.
- 기대값: 약속별 한 요청만 200, 다른 요청은 409 `VERSION_CONFLICT`. currentVersion은 API 계약상 조건부이므로 존재 여부로 판정하지 않음.

## 변경

- 서버·DB 구조·인증 로직 변경 없음.
- `performance/rental/appointment-race.js`에 배치 전송·응답·충돌 검증 추가.
- `performance/rental/run_appointment.py`에 전용 데이터·임시 세션 생성 및 DB 후검증 추가.

## 결과

| 항목 | 결과 |
|---|---:|
| 요청 수 | 40건 |
| 성공 / 예상 버전 충돌 | 20건 / 20건 |
| 수락 성공 / 철회 성공 | 10건 / 10건 |
| 예상 밖 응답 | 0건 |
| 약속별 검증 | 40/40 통과 |
| 전체 평균 | 45.17ms |
| 전체 p95 / p99 | 73.82ms / 318.98ms |
| 성공 평균 / p95 | 42.46ms / 58.14ms |
| 버전 충돌 평균 / p95 | 47.87ms / 73.94ms |
| 최대 응답 시간 | 319.88ms |
| 관측 요청률 | 40.83req/s |

- 최종 제안 상태: CONFIRMED 10개·WITHDRAWN 10개. PROPOSED 잔존 없음.
- 20개 전달 슬롯 모두 version 2. 동일 슬롯에 두 변경이 모두 적용된 경우 없음.
- 확정된 슬롯은 해당 제안 ID 참조 및 accepted_by가 대여자와 일치.
- 철회된 슬롯은 확정 참조·accepted_by 없음.
- 관련 대여는 모두 APPROVED 유지.
- 슬롯별 최종 상태와 일치하는 version 2 변경 이벤트 1건씩, 전체 변경 이벤트 20건·성공 멱등 기록 20건 확인.
- 임시 세션 2개 폐기·인증 fixture 삭제 완료.
- 예상 409가 HTTP 실패로 집계되어 `http_req_failed=50%`. 서비스 장애율과 구분. 예상 밖 응답률 0%·검증 통과 여부로 판정.

원본: [k6 결과](raw/appointment-race.json), [DB 검증](raw/appointment-verification.json).

## 재현

최초 전용 계정·동네 생성은 [목록 측정](rental-list.md#재현) 참고. 기존 전용 데이터가 있는 경우 저장소 루트에서 실행.

```bash
cd backend
./gradlew bootJar
cd ..
docker compose -f performance/rental/compose.yaml up -d --build --wait
python3 performance/rental/run_appointment.py
docker compose -f performance/rental/compose.yaml stop
```

- 실행마다 새 데이터 추가. 원본 로그는 Git 제외 경로 `performance/rental/local/` 보관.
- 임시 토큰은 2시간 만료, DB에는 SHA-256만 저장. 인증 fixture는 실행 중에만 생성 후 삭제.

## 한계

- 약 0.98초·20개 약속의 짧은 경쟁 시험. 준비 구간 별도 없음. p99는 40건 표본의 보간값으로 해석 필요.
- 관측 요청률은 성공과 예상 충돌을 합한 값. 최대 처리량·장시간 안정성 지표 아님.
- 두 사용자·동시 요청 2개·전달 약속만 검증. 여러 사용자·반납 약속·기존 확정 약속 교체·동시 제안 생성은 별도 대상.
- 모든 작업이 같은 사용자 쌍을 사용하므로 회원 행 잠금 영향 포함. 약속 슬롯 잠금 비용만 분리 불가.
- DB 잠금 대기 시간·CPU·메모리 시계열·요청별 SQL 수 미측정.
- 동일 키 재전송과 버전 충돌 후 재조회·재시도는 별도 시나리오.
- 실행마다 데이터가 쌓이므로 성능 개선 전후 비교에는 동일한 초기 DB 상태 필요.
