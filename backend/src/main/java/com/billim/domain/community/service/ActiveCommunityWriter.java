package com.billim.domain.community.service;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * {@code members.active_community_id} 갱신. 위치 인증 성공 시 선택한 동네를 회원에 반영한다.
 * (API 명세 E_011 — "members.active_community_id 갱신")
 *
 * <p>회원 도메인의 엔티티·Repository를 바꾸지 않고 이 도메인 안에서 처리한다. 건드리는 컬럼은
 * 소속 선택 결과 하나뿐이고, 표시 이름·상태·version은 회원 도메인의 몫으로 남긴다.
 *
 * <p>TODO(A 회원): {@code Member}에 소속 선택 상태 변경 메서드가 생기면 그쪽으로 옮긴다.
 * 엔티티가 자기 상태를 바꾸는 편이 낫다(CONTRIBUTING 5 — 의미가 드러나는 상태 변경 메서드).
 */
@Component
public class ActiveCommunityWriter {

    private final EntityManager entityManager;

    public ActiveCommunityWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 회원 행을 잠근다. 활성 소속이 둘이 되지 않게 회원 단위로 직렬화한다.
     * 엔티티를 관리 상태로 올리지 않고 잠금만 걸어, 뒤따르는 갱신과 상태가 어긋나지 않게 한다.
     *
     * @throws BusinessException 회원이 없으면 401 (세션은 있으나 회원이 사라진 경우)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(long memberId) {
        List<Long> locked = entityManager
                .createQuery("SELECT m.id FROM Member m WHERE m.id = :memberId", Long.class)
                .setParameter("memberId", memberId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        if (locked.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /**
     * 선택한 동네를 반영한다. version은 올리지 않는다 — 표시 이름 수정의 낙관적 잠금과
     * 경합시키지 않기 위해서다. 소속 선택의 직렬화는 {@link #lock(long)}이 담당한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void select(long memberId, long communityId, Instant now) {
        entityManager.createQuery("""
                        UPDATE Member m
                           SET m.activeCommunityId = :communityId, m.updatedAt = :now
                         WHERE m.id = :memberId
                        """)
                .setParameter("communityId", communityId)
                .setParameter("now", now)
                .setParameter("memberId", memberId)
                .executeUpdate();
    }
}
