-- 위치 인증 기준을 법정동(읍면동)에서 행정동으로 되돌린다.
-- (도메인 가이드 결정 '행정동 위치 인증')
--
-- 되돌리는 이유는 검색 적중률과 동네 단위다.
--  1. 카카오 주소 검색은 행정동 기준일 때 두 이름을 모두 잡는다.
--     "역삼동"(법정동 이름) → 역삼1동·역삼2동, "역삼1동"(행정동 이름) → 역삼1동.
--     법정동 기준이면 행정동 이름 검색이 0건이다 (b_code가 비어 오기 때문).
--  2. 법정동은 행정동보다 넓다. 역삼1동·역삼2동이 역삼동 하나가 되어 동네 단위가 커진다.
--
-- 법정동 코드(8자리)는 행정동 체계에서 의미가 없어 비운다. 잘라 쓸 수도 없다.
-- 비운 동네는 반경 판정으로만 동작하고, 다시 조회될 때 행정동 코드로 등록된다.

-- 법정동으로 자동 등록됐고 아직 아무것도 참조하지 않는 동네는 지운다.
-- 남겨 두면 같은 장소에 "역삼동"(법정동, 코드 없음)과 "역삼1동"(행정동)이 공존해
-- 이웃이 서로 다른 동네로 갈린다. 참조가 있는 동네는 이미 쓰는 사람이 있으므로 남긴다.
DELETE FROM communities
WHERE region_code IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM community_memberships cm WHERE cm.community_id = communities.id)
  AND NOT EXISTS (SELECT 1 FROM community_places cp WHERE cp.community_id = communities.id)
  AND NOT EXISTS (SELECT 1 FROM notices n WHERE n.community_id = communities.id)
  AND NOT EXISTS (SELECT 1 FROM items i WHERE i.community_id = communities.id)
  AND NOT EXISTS (SELECT 1 FROM rentals r WHERE r.community_id = communities.id)
  AND NOT EXISTS (SELECT 1 FROM reports rp WHERE rp.community_id = communities.id)
  AND NOT EXISTS (SELECT 1 FROM members m WHERE m.active_community_id = communities.id);

-- 참조가 있어 남은 법정동 동네는 코드를 비워 반경 판정으로만 동작시킨다
UPDATE communities SET region_code = NULL WHERE region_code IS NOT NULL;

ALTER TABLE communities
    MODIFY COLUMN region_code CHAR(10) NULL COMMENT '행정동 코드 10자리. 역지오코딩 결과와 비교';
