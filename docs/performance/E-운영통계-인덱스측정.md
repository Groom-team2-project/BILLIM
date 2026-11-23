# E 운영 통계 인덱스 측정

> 버전 : v0.1.0\
> 수정일 : 2026.10.28

담당: 노현섭 · 대상: `GET /api/v1/admin/statistics` (E_069) · 시드는 [raw/stats-seed.sql](raw/stats-seed.sql)

## 1. 측정 이유

`rentals`에 `community_id`를 포함한 인덱스가 하나도 없었다. FK도 없어 MySQL이 자동 생성한 것조차
없다. 동네별 대여 집계 세 개가 모두 풀스캔이라 인덱스 추가의 효과를 먼저 확인했다.

## 2. 환경

| 항목 | 값 |
|---|---|
| DB | MySQL 8.4 (Docker, `billim-mysql-1`) · buffer pool 기본 |
| 측정 방법 | `EXPLAIN` + `SHOW PROFILES` (쿼리 단위). API 부하 측정은 아님 |
| 비교 방법 | 같은 세션에서 `IGNORE INDEX`로 인덱스 전후 비교 |

데이터 규모 — 동네 20 · 물건 400 · **대여 20,000** · 회원 50. 측정 동네 1곳의 대여는 1,000건,
기간(7일) 안의 대여는 22~23건이다.

## 3. 인덱스

```sql
ALTER TABLE rentals
    ADD KEY idx_rentals_community_created_status (community_id, created_at, status, item_id);
```

`created_at`을 `status`보다 앞에 뒀다. 통계의 대여 쿼리는 기간 조회가 둘, 누적 조회가 하나다.
기간이 보통 7~31일이라 범위로 먼저 좁히는 쪽이 유리하고, 누적 대여가 쌓여도 기간 쿼리 비용이
그대로다. `item_id`를 끝에 둬 인기 물건 집계가 테이블을 읽지 않는다.

## 4. 결과

### 4-1. 실행 계획

| 쿼리 | 전 | 후 |
|---|---|---|
| 활성 대여 | `ALL` · 19,824행 | `ref` · 1,000행 · **Using index** |
| 기간 일자별 요청·성사 | `ALL` · 19,824행 | `range` · **23행** · Using index |
| 인기 물건 | `ALL` · 19,824행 | `range` · **22행** · Using index |

세 쿼리 모두 커버링 인덱스로 처리된다. 인기 물건의 제목은 상위 3건만 PK로 다시 읽는다(`eq_ref`).

### 4-2. 소요 시간

| 쿼리 | 전 | 후 |
|---|---|---|
| 활성 대여 | 3.03 ms | 0.41 ms |
| 기간 일자별 | 3.44 ms | 0.25 ms |
| 인기 물건 | 3.87 ms | 0.20 ms |
| **대여 3개 합** | **10.34 ms** | **0.86 ms** (약 12배) |

응답 하나를 만드는 쿼리 8개 전체는 **2.3 ms**다.

| 쿼리 | 시간 | 인덱스 |
|---|---|---|
| 등록 물건(누적) | 0.29 ms | `idx_items_community_visibility_created` · Using index |
| 활성 대여 | 0.41 ms | `idx_rentals_community_created_status` · Using index |
| 신규 물건 | 0.18 ms | `idx_items_community_visibility_created` · Using index |
| 신규 이웃 | 0.23 ms | `idx_community_memberships_community_status_expires` |
| 미처리 신고 | 0.26 ms | `idx_reports_community_status_created` · Using index |
| 일자별 요청·성사 | 0.25 ms | `idx_rentals_community_created_status` · Using index |
| 카테고리별 물건 | 0.48 ms | categories 10행 스캔 + `idx_items_community_visibility_category_created` |
| 인기 물건 | 0.20 ms | `idx_rentals_community_created_status` · Using index |

## 5. 해석

절대값은 20,000건 기준이라 작다. 중요한 건 **기울기**다. 인덱스 전은 세 쿼리가 각각 전체
`rentals`를 읽어 누적 대여 수에 비례해 늘어난다. 인덱스 후는 기간 안의 행 수에만 비례해,
대여가 100만 건이 되어도 기간 쿼리 비용은 그대로다.

쿼리를 8개로 나눈 것은 의도다. 하나의 큰 조인으로 묶으면 각 테이블의 기존 인덱스를 못 쓰고
중간 결과가 커진다. 8개 모두 인덱스 조회라 왕복 비용이 지배적이지 않다.

## 6. 남은 것

- **API 단위 부하 측정 미실시.** 쿼리 수준만 봤다. 운영 화면은 동시 사용자가 관리자 소수라
  우선순위를 낮췄다. 측정한다면 k6로 `admin/statistics`를 기간 7일·31일로 비교한다.
- 카테고리 집계가 `categories`를 매번 10행 스캔한다. 분류는 거의 바뀌지 않아 캐시 후보지만,
  0.48 ms라 지금 넣을 이유가 없다.
- 측정 중 로컬 백엔드가 떠 있어 대여 요청 만료 스케줄러가 시드 중 200건을 `EXPIRED`로 바꿨다.
  행 수가 같아 인덱스 비교에는 영향이 없다.
