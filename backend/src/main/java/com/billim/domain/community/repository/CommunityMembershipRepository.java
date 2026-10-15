package com.billim.domain.community.repository;

import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, Long> {

    Optional<CommunityMembership> findByMemberIdAndCommunityId(long memberId, long communityId);

    /** 활성 소속은 회원당 하나. 재인증 시 이탈시킬 이전 소속을 찾는다 */
    Optional<CommunityMembership> findByMemberIdAndStatus(long memberId, MembershipStatus status);

    /**
     * 동네별 유효 인증 회원 수 (API 명세 Community.verifiedMemberCount).
     * 목록 응답에서 동네마다 세지 않도록 한 번에 모아 센다.
     */
    @Query("""
            SELECT m.communityId, COUNT(m)
              FROM CommunityMembership m
             WHERE m.communityId IN :communityIds
               AND m.status = com.billim.domain.community.entity.MembershipStatus.ACTIVE
               AND m.verificationExpiresAt > :now
             GROUP BY m.communityId
            """)
    List<Object[]> countValidByCommunityIds(@Param("communityIds") Collection<Long> communityIds,
                                            @Param("now") Instant now);
}
