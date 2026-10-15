package com.billim.domain.safety.repository;

import com.billim.domain.safety.entity.Block;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    /** 내가 차단한 방향만 (API 명세 E_052). 최신순 */
    Page<Block> findByBlockerIdOrderByCreatedAtDescIdDesc(long blockerId, Pageable pageable);

    Optional<Block> findByBlockerIdAndBlockedId(long blockerId, long blockedId);

    /**
     * 두 회원 사이에 어느 방향이든 차단이 있는지. 노출·채팅 제한 판정의 기준이다 (ERD 4.7).
     * UNIQUE (blocker_id, blocked_id)와 INDEX (blocked_id, blocker_id)로 양방향 모두 색인을 탄다.
     */
    @Query("""
            SELECT COUNT(b) > 0
              FROM Block b
             WHERE (b.blockerId = :memberId AND b.blockedId = :otherMemberId)
                OR (b.blockerId = :otherMemberId AND b.blockedId = :memberId)
            """)
    boolean existsBetween(@Param("memberId") long memberId,
                          @Param("otherMemberId") long otherMemberId);

    /**
     * 나와 차단 관계인 회원 ID 전체. 목록에서 차단 대상을 걸러낼 때 쓴다.
     * 회원마다 판정하지 않고 한 번에 모아 제외하도록 공개한다.
     */
    @Query("""
            SELECT CASE WHEN b.blockerId = :memberId THEN b.blockedId ELSE b.blockerId END
              FROM Block b
             WHERE b.blockerId = :memberId OR b.blockedId = :memberId
            """)
    List<Long> findRelatedMemberIds(@Param("memberId") long memberId);

    /** 주어진 회원들 중 나와 차단 관계인 사람만 (ERD 4.7 양방향 OR) */
    @Query("""
            SELECT CASE WHEN b.blockerId = :memberId THEN b.blockedId ELSE b.blockerId END
              FROM Block b
             WHERE (b.blockerId = :memberId AND b.blockedId IN :candidates)
                OR (b.blockedId = :memberId AND b.blockerId IN :candidates)
            """)
    List<Long> findRelatedMemberIdsAmong(@Param("memberId") long memberId,
                                         @Param("candidates") Collection<Long> candidates);
}
