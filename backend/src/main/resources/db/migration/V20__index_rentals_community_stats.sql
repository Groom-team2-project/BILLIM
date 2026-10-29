-- 동네 운영 통계(E_069)의 대여 집계를 인덱스로 처리한다.
--
-- rentals에는 community_id를 포함한 인덱스가 하나도 없었다. FK도 없어 MySQL이 자동 생성한
-- 것조차 없다. 그래서 동네별 대여 집계가 모두 풀스캔이었다.
--
-- 컬럼 순서는 created_at을 status보다 앞에 둔다. 통계의 대여 쿼리는 기간 조회가 둘(요청 수·
-- 성사 수·일자별, 인기 물건)이고 누적 조회가 하나(활성 대여)다. 기간이 보통 7~31일이라
-- created_at 범위로 먼저 좁히는 쪽이 유리하고, 누적 대여가 쌓여도 기간 쿼리 비용이 그대로다.
-- 활성 대여는 community_id까지만 쓰고 status를 인덱스 안에서 걸러낸다.
--
-- item_id를 마지막에 둬서 인기 물건 집계가 테이블을 읽지 않는다. (커버링 인덱스)
-- 추가 전용이라 기존 쿼리와 배포 순서에 영향이 없다.

ALTER TABLE rentals
    ADD KEY idx_rentals_community_created_status (community_id, created_at, status, item_id);
