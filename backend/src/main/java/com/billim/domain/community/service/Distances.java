package com.billim.domain.community.service;

/**
 * 두 좌표 사이의 지표면 거리(m). 하버사인 공식.
 *
 * <p>인증 반경 판정과 주변 동네 정렬에만 쓴다. 계산에 쓰인 사용자 좌표는 저장하지 않는다.
 * (정책 및 상태 5 위치 인증)
 *
 * <p>지구를 구로 근사하므로 수백 m~수십 km 범위에서 오차는 0.5% 안쪽이다.
 * 인증 반경이 보통 수백 m~수 km이므로 충분하다.
 */
final class Distances {

    /** 지구 평균 반지름 (m). WGS84 평균 */
    private static final double EARTH_RADIUS_M = 6_371_008.8;

    private Distances() {
    }

    static double metersBetween(double latitude1, double longitude1,
                                double latitude2, double longitude2) {
        double lat1 = Math.toRadians(latitude1);
        double lat2 = Math.toRadians(latitude2);
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(longitude2 - longitude1);

        double sinHalfLat = Math.sin(deltaLat / 2);
        double sinHalfLon = Math.sin(deltaLon / 2);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(lat1) * Math.cos(lat2) * sinHalfLon * sinHalfLon;

        // asin 형태는 a가 1을 아주 조금 넘는 수치 오차에도 안전하다
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * 반경을 감싸는 위도 폭(도). 좌표 범위 선필터용이다.
     *
     * <p>선필터는 후보를 줄이는 용도라 넉넉하게 잡아도 된다. 실제 거리 판정은 하버사인이 다시 한다.
     */
    static double latitudeSpan(double radiusM) {
        return Math.toDegrees(radiusM / EARTH_RADIUS_M);
    }

    /** 같은 반경의 경도 폭(도). 고위도로 갈수록 넓어진다 */
    static double longitudeSpan(double radiusM, double latitude) {
        double cos = Math.cos(Math.toRadians(latitude));
        if (cos < 1e-6) {
            return 180;   // 극지방에서는 경도로 좁히는 의미가 없다
        }
        return Math.min(180, latitudeSpan(radiusM) / cos);
    }
}
