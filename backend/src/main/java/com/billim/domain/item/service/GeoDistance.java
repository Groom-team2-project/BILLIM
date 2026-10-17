package com.billim.domain.item.service;

import java.math.BigDecimal;

// 두 좌표 사이 거리(m, 하버사인). 좌표 하나라도 없으면 null
final class GeoDistance {

    private static final double EARTH_RADIUS_M = 6_371_000;

    private GeoDistance() {
    }

    static Integer meters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double p1 = Math.toRadians(lat1.doubleValue());
        double p2 = Math.toRadians(lat2.doubleValue());
        double dp = p2 - p1;
        double dl = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return (int) Math.round(EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}
