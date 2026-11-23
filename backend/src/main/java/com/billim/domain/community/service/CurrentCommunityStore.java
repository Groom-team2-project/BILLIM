package com.billim.domain.community.service;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * <b>현재 동네</b>({@code members.active_community_id}) 읽기·쓰기.
 *
 * <p>저장 동네는 회원당 둘까지지만(V23) 현재 동네는 하나다. 홈 피드·물건 목록·등록의 기준이
 * 이 값이고, 거래·채팅 권한은 저장 동네 전체를 본다.
 * (도메인 가이드 결정 '저장 동네와 현재 동네')
 *
 * <p>회원 도메인의 엔티티·Repository를 바꾸지 않고 이 도메인 안에서 처리한다. 건드리는 컬럼은
 * 현재 동네 하나뿐이고, 표시 이름·상태·version은 회원 도메인의 몫으로 남긴다.
 * 회원 테이블을 보는 곳을 이 클래스 하나로 묶어 둔다.
 *
 * <p>TODO(A 회원): {@code Member}에 소속 선택 상태 변경 메서드가 생기면 그쪽으로 옮긴다.
 * 엔티티가 자기 상태를 바꾸는 편이 낫다(CONTRIBUTING 5 — 의미가 드러나는 상태 변경 메서드).
 */
@Component
public class CurrentCommunityStore {

    private final EntityManager entityManager;

    public CurrentCommunityStore(EntityManager entityManager) {
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

    /** 회원의 현재 동네. 아직 고른 적이 없으면 빈 Optional */
    @Transactional(readOnly = true)
    public Optional<Long> find(long memberId) {
        return entityManager
                .createQuery("SELECT m.activeCommunityId FROM Member m WHERE m.id = :memberId",
                        Long.class)
                .setParameter("memberId", memberId)
                .getResultList().stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * 여러 회원의 현재 동네를 한 번에. 후보 회원마다 조회하지 않기 위한 일괄 읽기다.
     * 현재 동네를 고르지 않은 회원은 결과에서 빠진다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> findAll(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT m.id, m.activeCommunityId FROM Member m
                         WHERE m.id IN :memberIds AND m.activeCommunityId IS NOT NULL
                        """, Object[].class)
                .setParameter("memberIds", memberIds)
                .getResultList();

        Map<Long, Long> current = new HashMap<>();
        for (Object[] row : rows) {
            current.put((Long) row[0], (Long) row[1]);
        }
        return current;
    }

    /**
     * 현재 동네를 바꾼다. version은 올리지 않는다 — 표시 이름 수정의 낙관적 잠금과
     * 경합시키지 않기 위해서다. 변경의 직렬화는 {@link #lock(long)}이 담당한다.
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
