package com.billim.domain.community.service;

import com.billim.domain.community.dto.CommunityPageResponse;
import com.billim.domain.community.dto.CommunityResponse;
import com.billim.domain.community.dto.NearbyCommunitiesResponse;
import com.billim.domain.community.dto.RegionLevel;
import com.billim.domain.community.dto.RegionOptionsResponse;
import com.billim.domain.community.dto.RegionScope;
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

    /** 검색 한 번에 보충 등록할 행정동 수 상한 */
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
     * <p>검색어에 해당하는 행정동이 아직 동네로 없으면 먼저 등록한다. 그래서 일부만 입력해도
     * 전국 행정동이 후보로 나온다. 등록은 첫 페이지에서만 하고, 다음 페이지는 등록된 결과를
     * 넘겨 읽는다. (도메인 가이드 결정 '행정동 위치 인증')
     *
     * <p>트랜잭션을 걸지 않는다. 보충 등록이 자기 트랜잭션에서 커밋한 행을 이어지는 검색이
     * 봐야 하는데, 하나의 읽기 스냅샷 안에서는 보이지 않는다.
     *
     * @param keyword 이름·공개 주소 부분 검색. 비우면 활성 동네 목록
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommunityPageResponse search(String keyword, RegionScope scope,
                                        boolean membersOnly, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        String trimmed = keyword == null ? "" : keyword.strip();
        requireTopDown(scope);

        // 지역 범위를 고른 검색은 보충하지 않는다. 범위 옵션(E_073)이 이미 등록된 동네에서
        // 나온 값이라, 그 안에 없는 행정동을 지도에서 찾아올 이유가 없다
        if (!scope.hasSido() && page == 0 && trimmed.length() >= SUPPLEMENT_MIN_KEYWORD) {
            supplementRegions(trimmed);
        }
        return toPage(communityRepository.search(CommunityStatus.ACTIVE, addressPrefix(scope),
                exactAddress(scope), trimmed, membersOnly, pageRequest), page, size);
    }

    /**
     * 동네 선택 단계 옵션 (E_073). 등록된 활성 동네에서 모은다.
     *
     * <p>전국 행정동 목록이 아니라 <b>이미 등록된 동네</b>의 단계다. 아직 아무도 인증하지 않은
     * 행정동은 나오지 않는다. 그 동네로 처음 들어가는 길은 이름 검색(E_009)이고, 검색이
     * 지도에서 찾아 등록한다. (도메인 가이드 결정 '동네 선택 방식')
     *
     * @param membersOnly 가입자가 있는 동네만 모은다. 검색만 해도 동네가 등록되므로 아무도 없는
     *                    동네가 섞인다. 기준은 {@code CommunityRepository#search} 참조
     */
    public RegionOptionsResponse findRegionOptions(RegionLevel level, RegionScope scope,
                                                   boolean membersOnly) {
        requireParent(level, scope);
        List<String> items = communityRepository
                .findDisplayAddresses(CommunityStatus.ACTIVE, addressPrefix(scope), membersOnly).stream()
                .map(RegionPath::of)
                .filter(path -> !path.isEmpty())
                .filter(path -> inScope(path, scope))
                .map(path -> valueAt(path, level))
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .toList();
        return new RegionOptionsResponse(level, items);
    }

    /** 고른 단계가 위에서 아래로 이어지는지. 위를 비우고 아래만 고르면 범위가 정해지지 않는다 */
    private void requireTopDown(RegionScope scope) {
        boolean broken = (scope.hasSigungu() && !scope.hasSido())
                || (scope.hasGu() && !scope.hasSigungu())
                || (scope.hasDong() && !scope.hasSido());
        if (broken) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 요청한 단계의 상위가 채워져 있는지.
     *
     * <p>읍·면·동은 시·도만 요구한다. 세종특별자치시는 시·군·구가 없어 시·도 바로 아래가
     * 읍·면·동이다.
     */
    private void requireParent(RegionLevel level, RegionScope scope) {
        requireTopDown(scope);
        boolean ready = switch (level) {
            case SIDO -> true;
            case SIGUNGU, DONG -> scope.hasSido();
            case GU -> scope.hasSido() && scope.hasSigungu();
        };
        if (!ready) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    /** 고른 단계와 공개 주소가 정말 같은지. 접두사 비교는 후보를 줄이는 용도라 여기서 확정한다 */
    private boolean inScope(RegionPath path, RegionScope scope) {
        return (!scope.hasSido() || path.sido().equals(scope.sido()))
                && (!scope.hasSigungu() || path.sigungu().equals(scope.sigungu()))
                && (!scope.hasGu() || path.gu().equals(scope.gu()))
                && (!scope.hasDong() || path.dong().equals(scope.dong()));
    }

    private String valueAt(RegionPath path, RegionLevel level) {
        return switch (level) {
            case SIDO -> path.sido();
            case SIGUNGU -> path.sigungu();
            case GU -> path.gu();
            case DONG -> path.dong();
        };
    }

    /**
     * 상위 단계를 이어 붙인 공개 주소 접두사. 끝에 공백을 둬 토큰 경계를 맞춘다
     * ("강남구 "는 "강남구갑…"에 걸리지 않는다).
     */
    private String addressPrefix(RegionScope scope) {
        if (!scope.hasSido()) {
            return "";
        }
        StringBuilder prefix = new StringBuilder(escapeLike(scope.sido())).append(' ');
        if (scope.hasSigungu()) {
            prefix.append(escapeLike(scope.sigungu())).append(' ');
        }
        if (scope.hasGu()) {
            prefix.append(escapeLike(scope.gu())).append(' ');
        }
        return prefix.toString();
    }

    /** 읍·면·동까지 고르면 공개 주소가 확정된다. 접두사가 아니라 같은지로 비교한다 */
    private String exactAddress(RegionScope scope) {
        if (!scope.hasDong()) {
            return "";
        }
        StringBuilder address = new StringBuilder(scope.sido());
        if (scope.hasSigungu()) {
            address.append(' ').append(scope.sigungu());
        }
        if (scope.hasGu()) {
            address.append(' ').append(scope.gu());
        }
        return address.append(' ').append(scope.dong()).toString();
    }

    /** LIKE 와일드카드를 글자로 쓰게 escape한다. 쿼리의 {@code escape '!'}와 짝이다 */
    private static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
     * <p>현재 좌표의 행정동이 아직 동네로 없으면 이 시점에 등록한다. 그래서 전국 어디서 불러도
     * 최소한 자기 행정동은 후보에 나온다. (도메인 가이드 결정 '행정동 위치 인증')
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

    /** 현재 좌표의 행정동을 동네로 확보한다. 지도 조회가 안 되면 아무것도 하지 않는다 */
    private void ensureCurrentRegion(VerifyLocationRequest location) {
        regionResolver.resolve(location.latitude(), location.longitude())
                .ifPresent(this::ensureRegistered);
    }

    /** 검색어에 해당하는 행정동을 동네로 확보한다. 상한을 둬 한 번의 검색이 많은 행을 만들지 않게 한다 */
    private void supplementRegions(String keyword) {
        for (Region region : regionResolver.search(keyword, SUPPLEMENT_LIMIT)) {
            ensureRegistered(region);
        }
    }

    /**
     * 행정동 동네가 없으면 등록한다.
     *
     * <p>같은 행정동에서 동시에 들어오면 하나만 저장에 성공한다. 실패한 쪽은 먼저 커밋된 행을
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
            // 다른 요청이 같은 행정동을 먼저 등록했다
            log.debug("행정동 동네가 이미 등록됨: regionCode={}", region.code());
        }
    }

    /** 좌표 사각형으로 후보를 줄인다. 전국 행정동이 등록되면 전체 조회는 쓸 수 없다 */
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
