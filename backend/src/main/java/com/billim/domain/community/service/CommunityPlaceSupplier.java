package com.billim.domain.community.service;

import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityPlace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 공용 거래 장소가 없는 동네를 지도 제공자의 공공기관으로 채운다.
 *
 * <p>동네가 자동 등록되면 공용 장소는 아무도 넣지 않는다. 물건 등록은 placeId가 필수라
 * 장소가 0개인 동네에서는 등록이 아예 막힌다. 그래서 장소 목록 조회에서 0개일 때 채운다.
 * 동네 자동 등록과 같은 방식이다 — 없으면 그때 만든다. (도메인 가이드 결정 '공용 장소 자동 보충')
 *
 * <p>여러 인스턴스를 전제로 만들었다. 저장은 장소 하나마다 독립 트랜잭션이라 유니크 충돌이
 * 다른 저장을 망치지 않고, 충돌이 있었으면 커밋된 전체를 다시 읽어 돌려준다. 재시도 억제는
 * 동네 행에 남겨 인스턴스가 공유한다 — 인메모리로 두면 인스턴스 수만큼 외부 호출이 늘어난다.
 *
 * <p>이 클래스 자체에는 트랜잭션을 걸지 않는다. 쓰기·읽기 단위는 {@link CommunityPlaceWriter}가
 * 각자의 트랜잭션으로 수행한다.
 */
@Component
public class CommunityPlaceSupplier {

    private static final Logger log = LoggerFactory.getLogger(CommunityPlaceSupplier.class);

    private final PlaceFinder placeFinder;
    private final CommunityPlaceWriter writer;
    private final Clock clock;

    /** 끌 수 있게 둔다. 외부 호출이 부담되면 운영에서 내린다 */
    private final boolean enabled;

    /** 한 동네에 채울 장소 수. 사용자가 고를 수 있을 만큼이면 된다 */
    private final int limit;

    /** 채우지 못한 동네를 다시 시도하기까지의 간격 */
    private final Duration retryAfter;

    public CommunityPlaceSupplier(
            PlaceFinder placeFinder,
            CommunityPlaceWriter writer,
            Clock clock,
            @Value("${billim.community.places.auto-supply.enabled:true}") boolean enabled,
            @Value("${billim.community.places.auto-supply.limit:3}") int limit,
            @Value("${billim.community.places.auto-supply.retry-after:PT1H}") Duration retryAfter) {
        this.placeFinder = placeFinder;
        this.writer = writer;
        this.clock = clock;
        this.enabled = enabled;
        this.limit = limit;
        this.retryAfter = retryAfter;
    }

    /**
     * 장소가 없는 동네를 채우고 그 동네의 활성 장소를 돌려준다.
     *
     * @return 채운 장소. 비어 있으면 후보가 없었거나 연동이 실패했거나 최근에 이미 시도했다
     */
    public List<CommunityPlace> supply(Community community) {
        Instant now = clock.instant();
        long communityId = community.getId();

        if (!enabled || !community.canAttemptPlaceSupply(now, retryAfter)) {
            return List.of();
        }
        // 외부를 부르기 전에 남긴다. 다른 인스턴스가 같은 동네를 동시에 시도하지 않게
        writer.markAttempted(communityId, now);

        List<PlaceCandidate> candidates = placeFinder.findNear(
                community.centerLatitudeValue(), community.centerLongitudeValue(),
                PlaceBoundary.of(community.getDisplayAddress(), community.getName()), limit);

        if (candidates.isEmpty()) {
            log.info("공용 장소 후보 없음: communityId={} name={}", communityId, community.getName());
            return List.of();
        }
        return saveAll(communityId, community.getName(), candidates);
    }

    /**
     * 후보를 하나씩 저장한다. 충돌이 있었으면 커밋된 전체를 다시 읽는다.
     *
     * <p>다시 읽는 이유: 다른 요청이 먼저 넣은 장소까지 사용자에게 보여야 한다. 내가 넣은 것만
     * 돌려주면 동시에 들어온 쪽은 장소가 방금 생겼는데도 빈 목록을 본다.
     */
    private List<CommunityPlace> saveAll(long communityId, String name, List<PlaceCandidate> candidates) {
        List<CommunityPlace> saved = new ArrayList<>();
        boolean conflicted = false;

        for (PlaceCandidate candidate : candidates) {
            try {
                saved.add(writer.save(communityId, candidate));
            } catch (DataIntegrityViolationException e) {
                // 다른 요청이 같은 장소를 먼저 넣었다. 그 트랜잭션만 되돌려지고 나머지는 계속된다
                conflicted = true;
                log.debug("공용 장소가 이미 있음: communityId={} name={}", communityId, candidate.name());
            }
        }
        if (conflicted) {
            List<CommunityPlace> all = writer.findActive(communityId);
            log.info("공용 장소 자동 보충(충돌 있음): communityId={} name={} 저장={}건 전체={}건",
                    communityId, name, saved.size(), all.size());
            return all;
        }
        log.info("공용 장소 자동 보충: communityId={} name={} 후보={}건 저장={}건",
                communityId, name, candidates.size(), saved.size());
        return saved;
    }
}
