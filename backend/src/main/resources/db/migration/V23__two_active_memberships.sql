-- 저장 동네를 회원당 2개까지 허용한다.
--
-- V22는 "활성 소속은 회원당 하나"를 유니크 인덱스로 못박았다. 생성 컬럼 active_member_id가
-- ACTIVE일 때만 member_id가 되고 그 컬럼에 UNIQUE를 걸어 하나를 강제했다.
--
-- "최대 2개"는 유니크 인덱스로 표현할 수 없다. 그래서 슬롯을 쓴다 — ACTIVE 소속마다 1 또는 2를
-- 부여하고 (member_id, active_slot)에 UNIQUE를 건다. 유니크 인덱스는 NULL을 여러 개 허용하므로
-- 이탈 행은 제한 없이 쌓인다. 슬롯은 계산할 수 없어 생성 컬럼이 아니라 실제 컬럼이고,
-- 빈 슬롯을 고르는 책임은 서비스에 있다 (CommunityVerificationService).
--
-- 용어: 저장 동네 = ACTIVE 소속(최대 2) / 현재 동네 = members.active_community_id(하나).
-- 거래·채팅 권한은 저장 동네를, 홈 피드·물건 목록은 현재 동네를 기준으로 한다.
-- (도메인 가이드 결정 '저장 동네와 현재 동네')
--
-- ⚠ 배포 주의: 이 마이그레이션은 구 코드와 공존할 수 없다.
-- 구 코드는 active_slot 없이 ACTIVE를 넣고, status='LEFT'로 바꿀 때 슬롯을 비우지 않는다.
-- 둘 다 아래 CHECK에 걸린다. 순차 배포라 먼저 올라간 서버가 이걸 적용한 뒤에도 다른 서버는
-- 구 코드로 돌기 때문에, 그 구간에 위치 인증(E_011)과 동네 변경이 실패한다. 조회는 정상이다.
-- (배포 전략 8장 '롤링 배포와 공존 불가 마이그레이션')

-- 1) V22의 하나 강제를 걷어낸다
ALTER TABLE community_memberships
    DROP INDEX uk_community_memberships_active_member;

ALTER TABLE community_memberships
    DROP COLUMN active_member_id;

-- 2) 슬롯 컬럼. ACTIVE가 아니면 NULL이다
ALTER TABLE community_memberships
    ADD COLUMN active_slot INT NULL
        COMMENT '저장 동네 슬롯 1~2. ACTIVE가 아니면 NULL' AFTER status;

-- 3) 기존 ACTIVE 소속에 슬롯을 준다. V22가 회원당 하나를 보장했으므로 모두 1번이다
UPDATE community_memberships
   SET active_slot = 1,
       updated_at = NOW(6)
 WHERE status = 'ACTIVE';

-- 4) 현재 동네가 비어 있는데 저장 동네가 있으면 채운다.
--    소속 판정 창구(findActiveCommunityId·findActiveMembership·findValidCommunityIds)가
--    members.active_community_id를 기준으로 바뀌었다. 비어 있으면 유효 소속이 없는 회원이 된다.
--    V22가 회원당 ACTIVE 하나를 보장했으므로 고를 행이 하나뿐이다.
UPDATE members m
    JOIN community_memberships cm ON cm.member_id = m.id AND cm.status = 'ACTIVE'
SET m.active_community_id = cm.community_id,
    m.updated_at = NOW(6)
WHERE m.active_community_id IS NULL;

-- 5) 회원당 슬롯 하나씩 → 저장 동네는 최대 2개
-- NULL 비교를 먼저 걸러낸다. CHECK는 결과가 NULL이면 통과하므로 active_slot IN (1,2)만
-- 쓰면 슬롯 없는 ACTIVE가 그대로 들어간다
ALTER TABLE community_memberships
    ADD CONSTRAINT ck_community_memberships_active_slot
        CHECK ((status = 'ACTIVE' AND active_slot IS NOT NULL AND active_slot BETWEEN 1 AND 2)
            OR (status <> 'ACTIVE' AND active_slot IS NULL));

ALTER TABLE community_memberships
    ADD UNIQUE KEY uk_community_memberships_active_slot (member_id, active_slot);
