package com.billim.domain.community.service;

import java.util.stream.Collectors;
import java.util.Collection;
import java.util.Map;
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
    private final CurrentCommunityStore currentCommunityStore;
    private final Clock clock;

    public CommunityMembershipService(CommunityMembershipRepository membershipRepository,
                                      CommunityRepository communityRepository,
                                      CurrentCommunityStore currentCommunityStore, Clock clock) {
        this.membershipRepository = membershipRepository;
        this.communityRepository = communityRepository;
        this.currentCommunityStore = currentCommunityStore;
        this.clock = clock;
    }

    /**
     * 현재 동네 하나를 돌려준다. 저장 동네가 둘이어도 하나다.
     *
     * <p>{@code members.active_community_id}로 찾고 그 동네의 소속이 유효한지 확인한다.
     * 저장 동네 중 하나를 고르는 것이 아니라 회원이 고른 값을 쓴다 — 그래서 목록·피드의 기준이
     * 전환 전까지 흔들리지 않는다. (도메인 가이드 결정 '저장 동네와 현재 동네')
     */
    @Override
    public Optional<Long> findActiveCommunityId(long memberId) {
        return findCurrentMembership(memberId).map(CommunityMembership::getCommunityId);
    }

    /** 동네 상태는 미검사 — findActiveCommunityId와 같은 기준. 동네 행 없으면 이름 조립 불가로 빈 Optional */
    @Override
    public Optional<MembershipResponse> findActiveMembership(long memberId) {
        Instant now = clock.instant();
        return findCurrentMembership(memberId)
                .flatMap(membership -> communityRepository.findById(membership.getCommunityId())
                        .map(community -> MembershipResponse.of(membership, community, now)));
    }

    /** 현재 동네의 유효 소속. 현재 동네를 안 골랐거나 그 소속이 만료되면 빈 Optional */
    private Optional<CommunityMembership> findCurrentMembership(long memberId) {
        Instant now = clock.instant();
        return currentCommunityStore.find(memberId)
                .flatMap(communityId ->
                        membershipRepository.findByMemberIdAndCommunityId(memberId, communityId))
                .filter(membership -> membership.isValidAt(now));
    }

    @Override
    public boolean hasValidMembership(long memberId, long communityId) {
        return membershipRepository.findByMemberIdAndCommunityId(memberId, communityId)
                .filter(membership -> membership.isValidAt(clock.instant()))
                .isPresent();
    }

    /**
     * 회원마다 현재 동네 하나. 저장 동네가 둘이어도 하나다.
     *
     * <p>{@code members.active_community_id}로 묶는다. 저장 동네를 그대로 모으면 회원 하나에
     * 값이 둘이 되어 Map을 만들 수 없다.
     */
    @Override
    public Map<Long, Long> findValidCommunityIds(Collection<Long> memberIds) {
        if (memberIds.isEmpty()) return Map.of();
        Instant now = clock.instant();
        Map<Long, Long> current = currentCommunityStore.findAll(memberIds);
        if (current.isEmpty()) return Map.of();

        // 현재 동네의 소속이 유효한 회원만 남긴다
        return membershipRepository
                .findByMemberIdInAndStatus(current.keySet(), MembershipStatus.ACTIVE).stream()
                .filter(membership -> membership.isValidAt(now))
                .filter(membership ->
                        membership.getCommunityId().equals(current.get(membership.getMemberId())))
                .collect(Collectors.toMap(CommunityMembership::getMemberId,
                        CommunityMembership::getCommunityId));
    }
}
