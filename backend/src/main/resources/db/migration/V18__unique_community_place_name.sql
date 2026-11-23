-- 공용 장소 자동 보충이 같은 장소를 두 번 넣지 않게 막는다.
-- 동네가 자동 등록되면 공용 장소는 아무도 넣지 않아 0개로 남는다. 그러면 물건 등록이
-- placeId 필수 때문에 아예 막히므로, 장소 목록 조회에서 0개일 때 지도 제공자의
-- 공공기관 검색으로 채운다. 같은 동네에 같은 이름을 두 번 넣지 않도록 유니크를 둔다.
-- (도메인 가이드 결정 '공용 장소 자동 보충')
--
-- 기존 데이터에 중복이 있으면 ALTER가 실패한다. 먼저 중복을 정리한다.
-- 같은 (동네, 이름) 중에서 가장 작은 id만 남긴다. 물건·약속이 참조하는 장소가
-- 지워지지 않도록 참조가 있는 행은 남긴다.

DELETE p FROM community_places p
    JOIN (
        SELECT community_id, name, MIN(id) AS keep_id
        FROM community_places
        GROUP BY community_id, name
        HAVING COUNT(*) > 1
    ) d ON d.community_id = p.community_id AND d.name = p.name
WHERE p.id <> d.keep_id
  AND NOT EXISTS (SELECT 1 FROM items i WHERE i.place_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM rentals r WHERE r.place_id = p.id)
  AND NOT EXISTS (SELECT 1 FROM appointment_proposals a WHERE a.place_id = p.id);

ALTER TABLE community_places
    ADD UNIQUE KEY uk_community_places_community_name (community_id, name);
