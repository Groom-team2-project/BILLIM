-- 행정동 코드 기반 위치 인증. 좌표 반경 판정과 병행한다.
-- 코드가 일치하면 통과, 없거나 불일치면 기존 반경 판정으로 떨어진다. (도메인 가이드 결정 '행정동 위치 인증')
-- NULL 허용: 반경만으로 운영하는 기존 동네 행을 그대로 둔다.

ALTER TABLE communities
    ADD COLUMN region_code CHAR(10) NULL COMMENT '행정동 코드 10자리. 역지오코딩 결과와 비교' AFTER display_address,
    ADD UNIQUE KEY uk_communities_region_code (region_code);
