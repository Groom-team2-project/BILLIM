package com.billim.domain.item.dto;

import com.billim.domain.community.entity.CommunityPlace;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

// 거래 장소 (API 명세 Place)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceResponse(String id, String communityId, String name,
                            BigDecimal latitude, BigDecimal longitude, String guide) {

    public static PlaceResponse from(CommunityPlace place) {
        return new PlaceResponse(String.valueOf(place.getId()), String.valueOf(place.getCommunityId()),
                place.getName(), place.getLatitude(), place.getLongitude(), place.getGuide());
    }

    // 장소 행을 찾지 못한 경우(데이터 정합성 이상). id·communityId만 응답
    public static PlaceResponse of(long placeId, long communityId) {
        return new PlaceResponse(String.valueOf(placeId), String.valueOf(communityId), null, null, null, null);
    }
}
