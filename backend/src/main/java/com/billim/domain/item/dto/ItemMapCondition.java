package com.billim.domain.item.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// 지도 집계 조건 (API 명세 B_018). 지도 범위(bbox) 4개는 함께 지정하거나 모두 생략
public record ItemMapCondition(
        String keyword,
        Long categoryId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal minLat,
        BigDecimal maxLat,
        BigDecimal minLng,
        BigDecimal maxLng
) {
    public static final int MAX_PLACES = 200;

    public boolean hasBounds() {
        return minLat != null;
    }

    public boolean contains(BigDecimal lat, BigDecimal lng) {
        return !hasBounds() || (lat.compareTo(minLat) >= 0 && lat.compareTo(maxLat) <= 0
                && lng.compareTo(minLng) >= 0 && lng.compareTo(maxLng) <= 0);
    }
}
