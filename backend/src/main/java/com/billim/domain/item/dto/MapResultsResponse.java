package com.billim.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

// 지도 집계 결과 (API 명세 MapResults·MapPlace)
public record MapResultsResponse(List<MapPlace> items, boolean truncated, String distanceBasis) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MapPlace(PlaceResponse place, int matchingItemCount, Integer distanceMeters) {
    }
}
