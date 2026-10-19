package com.billim.domain.community.service;

import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.MembershipStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** 소속 판정 구현. 유효 기준은 {@link CommunityMembership#isValidAt} 하나로 모아 둔다 */
@Service
@Transactional(readOnly = true)
public class CommunityMembershipService implements CommunityMembershipQuery {

    private final CommunityMembershipRepository membershipRepository;
    private final CommunityRepository communityRepository;
    private final Clock clock;

    public CommunityMembershipService(CommunityMembershipRepository membershipRepository,
                                      CommunityRepository communityRepository, Clock clock) {
        this.membershipRepository = membershipRepository;
        this.communityRepository = communityRepository;
        this.clock = clock;
    }

    @Override
    public Optional<Long> findActiveCommunityId(long memberId) {
        return membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)
                .filter(membership -> membership.isValidAt(clock.instant()))
                .map(CommunityMembership::getCommunityId);
    }

    /** 동네 상태는 미검사 — findActiveCommunityId와 같은 기준. 동네 행 없으면 이름 조립 불가로 빈 Optional */
    @Override
    public Optional<MembershipResponse> findActiveMembership(long memberId) {
        Instant now = clock.instant();
        return membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)
                .filter(membership -> membership.isValidAt(now))
                .flatMap(membership -> communityRepository.findById(membership.getCommunityId())
                        .map(community -> MembershipResponse.of(membership, community, now)));
    }

    @Override
    public boolean hasValidMembership(long memberId, long communityId) {
        return membershipRepository.findByMemberIdAndCommunityId(memberId, communityId)
                .filter(membership -> membership.isValidAt(clock.instant()))
                .isPresent();
    }
}
