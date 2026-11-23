package com.billim.domain.community.dto;

import java.util.List;

/** 공용 거래 장소 목록 (API 명세 PlaceList) */
public record PlaceListResponse(List<PlaceResponse> items) {
}
