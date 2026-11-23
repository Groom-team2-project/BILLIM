-- "활성 소속은 회원당 하나"를 DB가 보장하게 한다.
--
-- 지금까지 이 규칙은 서비스의 회원 행 잠금으로만 지켰다. DB 유니크는
-- (member_id, community_id)라서 서로 다른 동네의 ACTIVE 두 개를 막지 못한다.
-- 한 번 깨지면 CommunityMembershipService.findActiveMembership이 단건을 기대해
-- GET /members/me(A_005, P0)가 500으로 죽는다. 실제로 겪었다.
--
-- MySQL에는 부분 유니크 인덱스가 없어 생성 컬럼으로 대신한다. ACTIVE가 아니면 NULL이고
-- 유니크 인덱스는 NULL을 여러 개 허용하므로, 결과적으로 "회원당 ACTIVE 하나"가 된다.

-- 1) 기존 위반 정리. 활성 소속이 둘 이상인 회원에서 하나만 남긴다.
--    남길 행은 members.active_community_id와 같은 동네를 우선하고(앱이 그 값을 쓴다),
--    없으면 가장 최근에 인증한 행이다. 나머지는 이탈로 돌린다.
--
--    갱신 대상 테이블을 서브쿼리에서 직접 읽으면 ER_UPDATE_TABLE_USED다. 파생 테이블로
--    감싸 피하는데, 단순 파생 테이블은 옵티마이저가 병합해(derived_merge) 다시 같은
--    오류가 날 수 있다. 창 함수가 있으면 병합 대상이 아니라 반드시 구체화된다.
--    등수를 한 번에 매기므로 바깥 행마다 서브쿼리를 돌리지도 않는다.
UPDATE community_memberships cm
    JOIN (
        SELECT cm2.id,
               ROW_NUMBER() OVER (
                   PARTITION BY cm2.member_id
                   ORDER BY (cm2.community_id <=> m2.active_community_id) DESC,
                            cm2.verified_at DESC,
                            cm2.id DESC
               ) AS rn
        FROM community_memberships cm2
            JOIN members m2 ON m2.id = cm2.member_id
        WHERE cm2.status = 'ACTIVE'
    ) dup ON dup.id = cm.id
SET cm.status = 'LEFT',
    cm.left_at = COALESCE(cm.left_at, NOW(6)),
    cm.updated_at = NOW(6)
WHERE dup.rn > 1;

-- 2) 남은 활성 소속과 members.active_community_id를 맞춘다.
--    1)의 우선순위 덕에 보통 이미 같다. 활성 소속에 없는 동네를 가리키던 경우만 교정한다.
UPDATE members m
    JOIN community_memberships cm ON cm.member_id = m.id AND cm.status = 'ACTIVE'
SET m.active_community_id = cm.community_id,
    m.updated_at = NOW(6)
WHERE m.active_community_id IS NOT NULL
  AND m.active_community_id <> cm.community_id;

-- 3) ACTIVE일 때만 값이 생기는 생성 컬럼. VIRTUAL이라 저장 공간을 쓰지 않는다
ALTER TABLE community_memberships
    ADD COLUMN active_member_id BIGINT
        GENERATED ALWAYS AS (IF(status = 'ACTIVE', member_id, NULL)) VIRTUAL
        COMMENT '활성 소속 유일성 전용. ACTIVE가 아니면 NULL';

-- 4) 회원당 ACTIVE 하나
ALTER TABLE community_memberships
    ADD UNIQUE KEY uk_community_memberships_active_member (active_member_id);
