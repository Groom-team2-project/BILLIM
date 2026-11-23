package com.billim.domain.community.service;

import com.billim.domain.community.dto.MyCommunitiesResponse;
import com.billim.domain.community.dto.MyCommunitiesResponse.MyCommunity;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.MembershipStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 내 동네 조회·전환·이탈 (API 명세 E_074 ~ E_076).
 *
 * <p>저장 동네는 둘까지고(V23) 현재 동네는 하나다. 추가는 위치 인증(E_011)으로만 되고
 * 이 서비스는 이미 저장된 동네를 다룬다.
 * (도메인 가이드 결정 '저장 동네와 현재 동네')
 */
@Service
@Transactional(readOnly = true)
public class MyCommunityService {

    private static final Logger log = LoggerFactory.getLogger(MyCommunityService.class);

    private final CommunityMembershipRepository membershipRepository;
    private final CommunityRepository communityRepository;
    private final CurrentCommunityStore currentCommunityStore;
    private final Clock clock;

    public MyCommunityService(CommunityMembershipRepository membershipRepository,
                              CommunityRepository communityRepository,
                              CurrentCommunityStore currentCommunityStore, Clock clock) {
        this.membershipRepository = membershipRepository;
        this.communityRepository = communityRepository;
        this.currentCommunityStore = currentCommunityStore;
        this.clock = clock;
    }

    /** 저장 동네 목록과 현재 동네 (E_074). 인증이 만료된 저장 동네도 담는다 — 재인증 대상이다 */
    public MyCommunitiesResponse findMine(long memberId) {
        return toResponse(savedOf(memberId), currentCommunityStore.find(memberId).orElse(null));
    }

    /**
     * 현재 동네를 바꾼다 (E_075). 저장 동네 중에서만 고를 수 있다.
     *
     * <p>인증이 만료된 저장 동네로도 전환할 수 있다. 전환 자체는 피드 기준을 바꾸는 일이고,
     * 거래는 각 명령 API가 유효 소속을 다시 검사한다.
     */
    @Transactional
    public MyCommunitiesResponse select(long memberId, long communityId) {
        currentCommunityStore.lock(memberId);
        List<CommunityMembership> saved = savedOf(memberId);
        requireSaved(saved, communityId);

        currentCommunityStore.select(memberId, communityId, clock.instant());
        log.info("현재 동네 변경: memberId={} communityId={}", memberId, communityId);
        return toResponse(saved, communityId);
    }

    /**
     * 저장 동네에서 뺀다 (E_076). 마지막 하나는 뺄 수 없다.
     *
     * <p>마지막까지 빼면 미인증 상태가 되어 아무것도 할 수 없다. 동네를 옮기려면 새 동네를
     * 먼저 인증한 뒤(슬롯 둘) 예전 동네를 뺀다.
     *
     * <p>현재 동네를 뺐으면 남은 동네가 현재 동네가 된다. 비워 두면 피드 기준이 사라진다.
     */
    @Transactional
    public MyCommunitiesResponse leave(long memberId, long communityId) {
        currentCommunityStore.lock(memberId);
        List<CommunityMembership> saved = savedOf(memberId);
        CommunityMembership target = requireSaved(saved, communityId);

        if (saved.size() <= 1) {
            throw new BusinessException(ErrorCode.COMMUNITY_LAST_MEMBERSHIP);
        }
        Instant now = clock.instant();
        target.leave(now);
        membershipRepository.flush();

        List<CommunityMembership> remaining = new ArrayList<>(saved);
        remaining.remove(target);

        Long current = currentCommunityStore.find(memberId).orElse(null);
        if (current == null || current == communityId) {
            current = remaining.getFirst().getCommunityId();
            currentCommunityStore.select(memberId, current, now);
        }
        log.info("저장 동네 이탈: memberId={} communityId={}", memberId, communityId);
        return toResponse(remaining, current);
    }

    private List<CommunityMembership> savedOf(long memberId) {
        return membershipRepository.findByMemberIdAndStatusOrderByActiveSlotAsc(
                memberId, MembershipStatus.ACTIVE);
    }

    /** 저장 동네가 아니면 404. 있지도 않은 동네와 남의 소속을 구분해 알려 주지 않는다 */
    private CommunityMembership requireSaved(List<CommunityMembership> saved, long communityId) {
        return saved.stream()
                .filter(membership -> membership.getCommunityId() == communityId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 동네 행이 사라진 소속은 이름을 조립할 수 없어 목록에서 빠진다 */
    private MyCommunitiesResponse toResponse(List<CommunityMembership> saved, Long currentCommunityId) {
        Instant now = clock.instant();
        Map<Long, Community> communities = communitiesOf(saved);

        List<MyCommunity> items = saved.stream()
                .filter(membership -> communities.containsKey(membership.getCommunityId()))
                .map(membership -> MyCommunity.of(membership,
                        communities.get(membership.getCommunityId()), currentCommunityId, now))
                .toList();
        return new MyCommunitiesResponse(items,
                currentCommunityId == null ? null : String.valueOf(currentCommunityId));
    }

    private Map<Long, Community> communitiesOf(List<CommunityMembership> saved) {
        List<Long> ids = saved.stream().map(CommunityMembership::getCommunityId).toList();
        Map<Long, Community> communities = new HashMap<>();
        for (Community community : communityRepository.findAllById(ids)) {
            communities.put(community.getId(), community);
        }
        return communities;
    }
}
