package com.billim.domain.community.service;

import com.billim.domain.community.entity.CommunityPlace;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.domain.community.repository.CommunityRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 공용 장소 자동 보충의 쓰기·읽기 단위. 하나하나가 독립 트랜잭션이다.
 *
 * <p>후보를 한 트랜잭션에 모아 넣으면 안 된다. 유니크 충돌이 한 번 나면 그 트랜잭션은
 * 되돌려야 하고, 같은 트랜잭션에서 이어지는 저장도 성립하지 않는다. 그래서 장소 하나마다
 * 트랜잭션을 따로 쓰고, 충돌은 호출 측이 트랜잭션 밖에서 받는다.
 *
 * <p>조회도 독립 트랜잭션이다. 호출 측의 읽기 스냅샷 안에서는 방금 커밋된 행이 보이지 않는다.
 * (도메인 가이드 결정 '공용 장소 자동 보충')
 */
@Component
public class CommunityPlaceWriter {

    private final CommunityPlaceRepository placeRepository;
    private final CommunityRepository communityRepository;

    public CommunityPlaceWriter(CommunityPlaceRepository placeRepository,
                                CommunityRepository communityRepository) {
        this.placeRepository = placeRepository;
        this.communityRepository = communityRepository;
    }

    /**
     * 장소 하나를 저장한다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 같은 동네에 같은 이름이 이미 있는 경우.
     *         호출 측이 트랜잭션 밖에서 받아야 한다
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CommunityPlace save(long communityId, PlaceCandidate candidate) {
        return placeRepository.save(CommunityPlace.of(
                communityId, candidate.name(),
                candidate.latitude(), candidate.longitude(), candidate.guide(), true));
    }

    /** 커밋된 활성 장소를 다시 읽는다. 동시 보충에서 다른 요청이 넣은 것까지 모두 본다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<CommunityPlace> findActive(long communityId) {
        return placeRepository.findByCommunityIdAndActiveTrueOrderByNameAscIdAsc(communityId);
    }

    /**
     * 보충을 시도했다고 표시한다. 외부를 부르기 전에 남겨 다른 인스턴스가 같은 동네를
     * 동시에 시도하지 않게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAttempted(long communityId, Instant now) {
        communityRepository.findById(communityId)
                .ifPresent(community -> community.markPlaceSupplyAttempted(now));
    }
}
