package com.billim.domain.community.dto;

import com.billim.domain.community.entity.CommunityPlace;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** 공용 거래 장소 (API 명세 Place). guide는 없으면 생략 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceResponse(
        String id,
        String communityId,
        String name,
        BigDecimal latitude,
        BigDecimal longitude,
        String guide
) {

    public static PlaceResponse from(CommunityPlace place) {
        return new PlaceResponse(
                String.valueOf(place.getId()),
                String.valueOf(place.getCommunityId()),
                place.getName(),
                place.getLatitude(),
                place.getLongitude(),
                place.getGuide());
    }
}
