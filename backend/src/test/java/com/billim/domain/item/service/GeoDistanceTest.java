package com.billim.domain.item.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// 동네 중심 거리 계산 (하버사인)
class GeoDistanceTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("같은 좌표는 0m다")
    void samePointIsZero() {
        assertThat(GeoDistance.meters(d("37.4979420"), d("127.0276210"), d("37.4979420"), d("127.0276210"))).isZero();
    }

    @Test
    @DisplayName("위도 0.001도 차이는 약 111m다")
    void oneThousandthDegreeLatitudeIsAbout111m() {
        assertThat(GeoDistance.meters(d("37.4970000"), d("127.0276210"), d("37.4980000"), d("127.0276210")))
                .isBetween(110, 112);
    }

    @Test
    @DisplayName("방향과 무관하게 같은 거리다")
    void distanceIsSymmetric() {
        Integer ab = GeoDistance.meters(d("37.4979420"), d("127.0276210"), d("37.5016000"), d("127.0262000"));
        Integer ba = GeoDistance.meters(d("37.5016000"), d("127.0262000"), d("37.4979420"), d("127.0276210"));
        assertThat(ab).isEqualTo(ba).isBetween(400, 450);
    }

    @Test
    @DisplayName("좌표 하나라도 없으면 null이다")
    void missingCoordinateIsNull() {
        assertThat(GeoDistance.meters(null, d("127.0"), d("37.5"), d("127.0"))).isNull();
    }
}
