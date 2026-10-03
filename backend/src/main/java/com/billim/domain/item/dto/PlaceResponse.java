package com.billim.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * 거래 장소 (API 명세 Place).
 * TODO(E): name·latitude·longitude·guide는 동네 도메인 연동 후 채운다. 연동 전에는 id·communityId만 내려간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceResponse(String id, String communityId, String name,
                            BigDecimal latitude, BigDecimal longitude, String guide) {

    public static PlaceResponse of(long placeId, long communityId) {
        return new PlaceResponse(String.valueOf(placeId), String.valueOf(communityId), null, null, null, null);
    }
}
