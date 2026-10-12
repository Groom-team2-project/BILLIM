package com.billim.domain.safety.service;

import com.billim.domain.member.entity.Member;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 행 잠금. 신고 접수와 제재 처리를 회원 단위로 직렬화한다.
 * (API 명세 12.4 "reporter 잠금 안에서 거부", 13.3 "대상 회원→report 잠금")
 *
 * <p>회원 도메인의 Repository에 잠금 조회를 추가하지 않고 이 도메인 안에서 해결한다.
 * 잠금만 걸고 회원 데이터는 바꾸지 않으므로 회원 도메인의 동작에 영향을 주지 않는다.
 *
 * <p>잠금 순서는 어디서든 회원 → (신고 | 제재)로 고정한다. 순서가 섞이면 교착이 생긴다.
 */
@Component
public class MemberRowLock {

    private final EntityManager entityManager;

    public MemberRowLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * 회원 행을 잠그고 돌려준다. 호출 측 트랜잭션에 반드시 참여한다.
     * 트랜잭션이 없으면 잠금이 곧바로 풀려 직렬화가 되지 않으므로 예외로 막는다.
     *
     * @throws BusinessException 회원이 없으면 404
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Member lock(long memberId) {
        Member member = entityManager.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE);
        if (member == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return member;
    }
}
