package com.billim.domain.community.service;

import com.billim.domain.community.dto.PlaceListResponse;
import com.billim.domain.community.dto.PlaceResponse;
import com.billim.domain.community.repository.CommunityPlaceRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공용 거래 장소 목록 (API 명세 E_012).
 *
 * <p>볼 수 있는 사람은 그 동네의 유효 소속이거나, 그 동네에서 거래한 당사자다.
 * 소속이 끝난 뒤에도 진행 중이던 거래의 전달·반납 장소는 확인할 수 있어야 한다.
 */
@Service
@Transactional(readOnly = true)
public class CommunityPlaceService {

    private final CommunityPlaceRepository placeRepository;
    private final CommunityQueryService communityQueryService;
    private final CommunityMembershipQuery membershipQuery;
    private final EntityManager entityManager;

    public CommunityPlaceService(CommunityPlaceRepository placeRepository,
                                 CommunityQueryService communityQueryService,
                                 CommunityMembershipQuery membershipQuery,
                                 EntityManager entityManager) {
        this.placeRepository = placeRepository;
        this.communityQueryService = communityQueryService;
        this.membershipQuery = membershipQuery;
        this.entityManager = entityManager;
    }

    /** 현재 active 장소만 이름순·id순 */
    public PlaceListResponse findPlaces(long memberId, long communityId) {
        communityQueryService.requireActive(communityId);
        requireAccess(memberId, communityId);

        List<PlaceResponse> places = placeRepository
                .findByCommunityIdAndActiveTrueOrderByNameAscIdAsc(communityId).stream()
                .map(PlaceResponse::from)
                .toList();

        return new PlaceListResponse(places);
    }

    /** 볼 권한이 없으면 존재를 알리지 않고 404 (명세 0.2) */
    private void requireAccess(long memberId, long communityId) {
        if (membershipQuery.hasValidMembership(memberId, communityId)) {
            return;
        }
        if (hasRentalInCommunity(memberId, communityId)) {
            return;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * 이 동네에서 거래한 이력이 있는지. 대여 도메인의 Repository를 바꾸지 않고 조회만 한다.
     * rentals.community_id는 거래 생성 당시의 소속이라 이후 소속이 바뀌어도 남는다.
     */
    private boolean hasRentalInCommunity(long memberId, long communityId) {
        Long count = entityManager.createQuery("""
                        SELECT COUNT(r)
                          FROM Rental r
                         WHERE r.communityId = :communityId
                           AND (r.ownerId = :memberId OR r.borrowerId = :memberId)
                        """, Long.class)
                .setParameter("communityId", communityId)
                .setParameter("memberId", memberId)
                .getSingleResult();

        return count > 0;
    }
}
