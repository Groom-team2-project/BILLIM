package com.billim.domain.item.service;

import com.billim.domain.item.port.CommunityPort.GeoPoint;
import com.billim.domain.item.port.CommunityPort.PlaceInfo;

/** 동네 중심에서 공용 장소까지의 거리(m). distanceBasis=COMMUNITY_CENTER */
public final class GeoDistance {

    public static final String BASIS = "COMMUNITY_CENTER";
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoDistance() {
    }

    public static int meters(GeoPoint center, PlaceInfo place) {
        double lat1 = Math.toRadians(center.latitude().doubleValue());
        double lat2 = Math.toRadians(place.latitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(place.longitude().doubleValue() - center.longitude().doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(EARTH_RADIUS_M * c);
    }
}
