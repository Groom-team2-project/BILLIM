package com.billim.domain.community.dto;

import java.util.List;

/**
 * 주변 동네 (API 명세 NearbyCommunities).
 * truncated면 후보가 더 있다는 뜻이고, 화면은 이름 검색으로 보완한다. (API 명세 E_071)
 */
public record NearbyCommunitiesResponse(
        List<CommunityResponse> items,
        boolean truncated
) {
}
