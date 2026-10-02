package com.billim.domain.item.port;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 커뮤니티·공용 장소 조회 계약. 구현은 커뮤니티 담당(E) 영역.
 * TODO(E): communities·community_places 기반 구현체 연결.
 */
public interface CommunityPort {

    /** 회원의 유효한 활성 커뮤니티 ID. 없으면 COMMUNITY_VERIFICATION_REQUIRED(403) */
    long requireActiveCommunityId(long memberId);

    /** 활성 커뮤니티가 유효하면 그 ID. 소속 만료·없음이면 empty (HIDDEN 정리 등 예외 허용용) */
    Optional<Long> findActiveCommunityId(long memberId);

    Optional<PlaceInfo> findPlace(long placeId);

    /** 장소 ID → 장소. 없는 ID는 결과에서 빠진다. */
    Map<Long, PlaceInfo> findPlaces(Collection<Long> placeIds);

    /** 커뮤니티 중심 좌표. 거리 계산 기준(COMMUNITY_CENTER) */
    Optional<GeoPoint> findCenter(long communityId);

    record PlaceInfo(long id, long communityId, String name, BigDecimal latitude, BigDecimal longitude,
                     String guide, boolean active) {
    }

    record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
    }
}
