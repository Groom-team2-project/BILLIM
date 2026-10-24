package com.billim.domain.community.service;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공용 거래 장소가 없는 동네를 지도 제공자의 공공기관으로 채운다.
 *
 * <p>동네가 자동 등록되면 공용 장소는 아무도 넣지 않는다. 물건 등록은 placeId가 필수라
 * 장소가 0개인 동네에서는 등록이 아예 막힌다. 그래서 장소 목록 조회에서 0개일 때 채운다.
 * 동네 자동 등록과 같은 방식이다 — 없으면 그때 만든다. (도메인 가이드 결정 '공용 장소 자동 보충')
 *
 * <p>채우지 못한 동네를 매 조회마다 다시 시도하면 외부 호출이 쌓인다. 시도한 동네를
 * 잠시 기억해 그 사이에는 다시 부르지 않는다. 섬처럼 20 km 안에 공공기관이 없는 동네와
 * 연동 장애가 여기 해당한다.
 */
@Component
public class CommunityPlaceSupplier {

    private static final Logger log = LoggerFactory.getLogger(CommunityPlaceSupplier.class);

    private final CommunityPlaceRepository placeRepository;
    private final PlaceFinder placeFinder;

    /** 끌 수 있게 둔다. 외부 호출이 부담되면 운영에서 내린다 */
    private final boolean enabled;

    /** 한 동네에 채울 장소 수. 사용자가 고를 수 있을 만큼이면 된다 */
    private final int limit;

    /** 채우지 못한 동네를 다시 시도하기까지의 간격 */
    private final Map<Long, Long> attempted;
    private final long retryAfterMillis;

    public CommunityPlaceSupplier(
            CommunityPlaceRepository placeRepository,
            PlaceFinder placeFinder,
            @Value("${billim.community.places.auto-supply.enabled:true}") boolean enabled,
            @Value("${billim.community.places.auto-supply.limit:3}") int limit,
            @Value("${billim.community.places.auto-supply.retry-after:PT1H}") Duration retryAfter,
            @Value("${billim.community.places.auto-supply.tracked-communities:1000}") int trackedCommunities) {
        this.placeRepository = placeRepository;
        this.placeFinder = placeFinder;
        this.enabled = enabled;
        this.limit = limit;
        this.retryAfterMillis = retryAfter.toMillis();
        this.attempted = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
                return size() > trackedCommunities;
            }
        });
    }

    /**
     * 장소가 없는 동네를 채우고 채운 장소를 돌려준다.
     *
     * <p>저장은 자기 트랜잭션에서 커밋한다. 조회 트랜잭션에 섞이면 유니크 충돌 한 번이
     * 조회 전체를 실패시킨다.
     *
     * <p>저장한 행을 그대로 돌려주므로 호출 측이 다시 조회하지 않는다. 읽기 스냅샷 안에서는
     * 방금 커밋한 행이 보이지 않기도 하고, 쿼리를 한 번 아끼는 편이 낫다.
     *
     * @return 채운 장소. 비어 있으면 후보가 없었거나 연동이 실패했다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<CommunityPlace> supply(Community community) {
        long communityId = community.getId();
        if (!enabled || !shouldAttempt(communityId)) {
            return List.of();
        }
        List<PlaceCandidate> candidates = placeFinder.findNear(
                community.centerLatitudeValue(), community.centerLongitudeValue(),
                PlaceBoundary.of(community.getDisplayAddress(), community.getName()), limit);

        if (candidates.isEmpty()) {
            log.info("공용 장소 후보 없음: communityId={} name={}", communityId, community.getName());
            return List.of();
        }
        List<CommunityPlace> saved = new ArrayList<>();
        for (PlaceCandidate candidate : candidates) {
            try {
                saved.add(placeRepository.saveAndFlush(CommunityPlace.of(
                        communityId, candidate.name(),
                        candidate.latitude(), candidate.longitude(), candidate.guide(), true)));
            } catch (DataIntegrityViolationException e) {
                // 다른 요청이 같은 장소를 먼저 넣었다. 이미 있으므로 넘긴다
                log.debug("공용 장소가 이미 있음: communityId={} name={}", communityId, candidate.name());
            }
        }
        log.info("공용 장소 자동 보충: communityId={} name={} 후보={}건 저장={}건",
                communityId, community.getName(), candidates.size(), saved.size());
        return saved;
    }

    /** 방금 시도한 동네는 건너뛴다 */
    private boolean shouldAttempt(long communityId) {
        long now = System.currentTimeMillis();
        Long last = attempted.get(communityId);
        if (last != null && now - last < retryAfterMillis) {
            return false;
        }
        attempted.put(communityId, now);
        return true;
    }
}
