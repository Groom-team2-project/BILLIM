package com.billim.domain.community.service;

import com.billim.domain.community.dto.CommunityPageResponse;
import com.billim.domain.community.dto.CommunityResponse;
import com.billim.domain.community.dto.NearbyCommunitiesResponse;
import com.billim.domain.community.dto.VerifyLocationRequest;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityStatus;
import com.billim.domain.community.entity.Region;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    private static final Logger log = LoggerFactory.getLogger(CommunityQueryService.class);

    /** 주변 동네 응답 최대 개수 (API 명세 E_071) */
    private static final int NEARBY_LIMIT = 20;

    /** 후보를 줄이는 좌표 사각형의 반경(m). 거리 정렬은 이 안에서 하버사인으로 한다 */
    private static final double NEARBY_BOX_RADIUS_M = 20_000;

    /** 검색 한 번에 보충 등록할 읍면동 수 상한 */
    private static final int SUPPLEMENT_LIMIT = 10;

    /** 이 길이 미만의 검색어로는 지도를 부르지 않는다. 한 글자는 후보가 너무 넓다 */
    private static final int SUPPLEMENT_MIN_KEYWORD = 2;

    private final CommunityRepository communityRepository;
    private final CommunityMembershipRepository membershipRepository;
    private final RegionResolver regionResolver;
    private final CommunityAutoRegistrar autoRegistrar;
    private final Clock clock;

    public CommunityQueryService(CommunityRepository communityRepository,
                                 CommunityMembershipRepository membershipRepository,
                                 RegionResolver regionResolver,
                                 CommunityAutoRegistrar autoRegistrar,
                                 Clock clock) {
        this.communityRepository = communityRepository;
        this.membershipRepository = membershipRepository;
        this.regionResolver = regionResolver;
        this.autoRegistrar = autoRegistrar;
        this.clock = clock;
    }

    /**
     * 동네 검색 (E_009). 위치 없이 검색할 수 있다.
     *
     * <p>검색어에 해당하는 읍면동이 아직 동네로 없으면 먼저 등록한다. 그래서 일부만 입력해도
     * 전국 읍면동이 후보로 나온다. 등록은 첫 페이지에서만 하고, 다음 페이지는 등록된 결과를
     * 넘겨 읽는다. (도메인 가이드 결정 '법정동 위치 인증')
     *
     * <p>트랜잭션을 걸지 않는다. 보충 등록이 자기 트랜잭션에서 커밋한 행을 이어지는 검색이
     * 봐야 하는데, 하나의 읽기 스냅샷 안에서는 보이지 않는다.
     *
     * @param keyword 이름·공개 주소 부분 검색. 비우면 활성 동네 목록
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommunityPageResponse search(String keyword, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String trimmed = keyword == null ? "" : keyword.strip();

        if (trimmed.isEmpty()) {
            return toPage(communityRepository.findByStatusOrderByNameAscIdAsc(
                    CommunityStatus.ACTIVE, pageRequest), page, size);
        }
        if (page == 0 && trimmed.length() >= SUPPLEMENT_MIN_KEYWORD) {
            supplementRegions(trimmed);
        }
        return toPage(communityRepository.searchByKeyword(
                CommunityStatus.ACTIVE, trimmed, pageRequest), page, size);
    }

    private CommunityPageResponse toPage(Page<Community> communities, int page, int size) {
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
     *
     * <p>현재 좌표의 읍면동이 아직 동네로 없으면 이 시점에 등록한다. 그래서 전국 어디서 불러도
     * 최소한 자기 읍면동은 후보에 나온다. (도메인 가이드 결정 '법정동 위치 인증')
     *
     * <p>트랜잭션을 걸지 않는다. 자동 등록이 자기 트랜잭션에서 커밋한 행을 이어지는 조회가
     * 봐야 하는데, 하나의 읽기 스냅샷 안에서는 보이지 않는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public NearbyCommunitiesResponse findNearby(VerifyLocationRequest location) {
        ensureCurrentRegion(location);

        List<Community> candidates = findBoxCandidates(location);

        List<Community> nearest = candidates.stream()
                .sorted(Comparator
                        .comparingDouble((Community community) -> distanceTo(community, location))
                        .thenComparing(Community::getId))
                .limit(NEARBY_LIMIT)
                .toList();

        return new NearbyCommunitiesResponse(
                toResponses(nearest), candidates.size() > NEARBY_LIMIT);
    }

    /** 현재 좌표의 읍면동을 동네로 확보한다. 지도 조회가 안 되면 아무것도 하지 않는다 */
    private void ensureCurrentRegion(VerifyLocationRequest location) {
        regionResolver.resolve(location.latitude(), location.longitude())
                .ifPresent(this::ensureRegistered);
    }

    /** 검색어에 해당하는 읍면동을 동네로 확보한다. 상한을 둬 한 번의 검색이 많은 행을 만들지 않게 한다 */
    private void supplementRegions(String keyword) {
        for (Region region : regionResolver.search(keyword, SUPPLEMENT_LIMIT)) {
            ensureRegistered(region);
        }
    }

    /**
     * 읍면동 동네가 없으면 등록한다.
     *
     * <p>같은 읍면동에서 동시에 들어오면 하나만 저장에 성공한다. 실패한 쪽은 먼저 커밋된 행을
     * 그대로 쓰면 된다. 등록 결과를 돌려주지 않고, 이어지는 조회가 등록된 행을 집어 간다.
     */
    private void ensureRegistered(Region region) {
        if (communityRepository.findByRegionCodeAndStatus(
                region.code(), CommunityStatus.ACTIVE).isPresent()) {
            return;
        }
        try {
            autoRegistrar.register(region);
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 같은 읍면동을 먼저 등록했다
            log.debug("읍면동 동네가 이미 등록됨: regionCode={}", region.code());
        }
    }

    /** 좌표 사각형으로 후보를 줄인다. 전국 읍면동이 등록되면 전체 조회는 쓸 수 없다 */
    private List<Community> findBoxCandidates(VerifyLocationRequest location) {
        double latitudeSpan = Distances.latitudeSpan(NEARBY_BOX_RADIUS_M);
        double longitudeSpan = Distances.longitudeSpan(NEARBY_BOX_RADIUS_M, location.latitude());

        return communityRepository.findWithinBox(
                CommunityStatus.ACTIVE,
                BigDecimal.valueOf(location.latitude() - latitudeSpan),
                BigDecimal.valueOf(location.latitude() + latitudeSpan),
                BigDecimal.valueOf(location.longitude() - longitudeSpan),
                BigDecimal.valueOf(location.longitude() + longitudeSpan));
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
