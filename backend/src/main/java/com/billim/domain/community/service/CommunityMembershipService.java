package com.billim.domain.community.service;

import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.MembershipStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/** 소속 판정 구현. 유효 기준은 {@link CommunityMembership#isValidAt} 하나로 모아 둔다 */
@Service
@Transactional(readOnly = true)
public class CommunityMembershipService implements CommunityMembershipQuery {

    private final CommunityMembershipRepository membershipRepository;
    private final Clock clock;

    public CommunityMembershipService(CommunityMembershipRepository membershipRepository, Clock clock) {
        this.membershipRepository = membershipRepository;
        this.clock = clock;
    }

    @Override
    public Optional<Long> findActiveCommunityId(long memberId) {
        return membershipRepository.findByMemberIdAndStatus(memberId, MembershipStatus.ACTIVE)
                .filter(membership -> membership.isValidAt(clock.instant()))
                .map(CommunityMembership::getCommunityId);
    }

    @Override
    public boolean hasValidMembership(long memberId, long communityId) {
        return membershipRepository.findByMemberIdAndCommunityId(memberId, communityId)
                .filter(membership -> membership.isValidAt(clock.instant()))
                .isPresent();
    }
}
