package com.billim.domain.community.service;

import com.billim.domain.community.dto.CommunityPageResponse;
import com.billim.domain.community.dto.CommunityResponse;
import com.billim.domain.community.dto.NearbyCommunitiesResponse;
import com.billim.domain.community.dto.VerifyLocationRequest;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 동네 검색·조회 (API 명세 E_009, E_010, E_071).
 * 로그인은 필요하지만 소속 인증 전에도 호출할 수 있다. 가입 흐름에서 쓰이기 때문이다.
 */
@Service
@Transactional(readOnly = true)
public class CommunityQueryService {

    /** 주변 동네 응답 최대 개수 (API 명세 E_071) */
    private static final int NEARBY_LIMIT = 20;

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final Clock clock;

    public CommunityQueryService(CommunityRepository communityRepository,
                                 CommunityMembershipRepository membershipRepository,
                                 Clock clock) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
    }

    /**
     * 동네 이름 검색 (E_009). 위치 없이 검색할 수 있다.
     *
     * @param keyword 이름 부분 검색. 비우면 활성 동네 목록
     */
    public CommunityPageResponse search(String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<Community> communities = keyword == null || keyword.isBlank()
                ? communityRepository.findByStatusOrderByNameAscIdAsc(CommunityStatus.ACTIVE, pageRequest)
                : communityRepository.findByStatusAndNameContainingOrderByNameAscIdAsc(
                        CommunityStatus.ACTIVE, keyword.strip(), pageRequest);

        return new CommunityPageResponse(
                toResponses(communities.getContent()),
                page, size, communities.getTotalElements(), communities.hasNext());
    }

    /** 동네 정보 조회 (E_010). 비활성 동네는 404 */
    public CommunityResponse findActive(long communityId) {
        Community community = requireActive(communityId);
        return CommunityResponse.of(community, countValid(List.of(community)).getOrDefault(communityId, 0L));
    }

    /** 다른 서비스가 인증 대상 동네를 가져올 때 쓰는 공통 조회 */
    Community requireActive(long communityId) {
        return communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * 현재 위치로 주변 동네 찾기 (E_071). 가까운 순·id순 최대 20개.
     *
     * <p>이 조회는 소속 인증이 아니다. 좌표와 정확도는 거리 계산에만 쓰고 저장·로그에 남기지 않는다.
     * 정확도가 낮아도 거부하지 않는다 — 후보를 보여주는 단계이고, 판정은 인증에서 한다.
     */
    public NearbyCommunitiesResponse findNearby(VerifyLocationRequest location) {
        List<Community> candidates = communityRepository.findByStatus(CommunityStatus.ACTIVE);

        List<Community> nearest = candidates.stream()
                .sorted(Comparator
                        .comparingDouble((Community community) -> distanceTo(community, location))
                        .thenComparing(Community::getId))
                .limit(NEARBY_LIMIT)
                .toList();

        return new NearbyCommunitiesResponse(
                toResponses(nearest), candidates.size() > NEARBY_LIMIT);
    }

    private double distanceTo(Community community, VerifyLocationRequest location) {
        return Distances.metersBetween(
                location.latitude(), location.longitude(),
                community.centerLatitudeValue(), community.centerLongitudeValue());
    }

    /** 동네마다 회원 수를 세지 않고 한 번에 모아 붙인다 */
    private List<CommunityResponse> toResponses(List<Community> communities) {
        if (communities.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = countValid(communities);
        return communities.stream()
                .map(community -> CommunityResponse.of(
                        community, counts.getOrDefault(community.getId(), 0L)))
                .toList();
    }

    private Map<Long, Long> countValid(List<Community> communities) {
        Instant now = clock.instant();
        List<Long> ids = communities.stream().map(Community::getId).toList();

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : membershipRepository.countValidByCommunityIds(ids, now)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
