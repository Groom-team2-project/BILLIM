package com.billim.domain.item.dto;

import com.billim.domain.item.port.CommunityPort.PlaceInfo;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlaceResponse(String id, String communityId, String name,
                            BigDecimal latitude, BigDecimal longitude, String guide) {

    public static PlaceResponse from(PlaceInfo p) {
        return new PlaceResponse(String.valueOf(p.id()), String.valueOf(p.communityId()), p.name(),
                p.latitude(), p.longitude(), p.guide());
    }
}
