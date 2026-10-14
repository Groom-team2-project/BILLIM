package com.billim.domain.community.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하버사인 거리. 기대값은 구 근사 공식에서 그대로 유도되는 값만 쓴다.
 * 위도 1도 = π·R/180 = 111,194.9m (R = 6,371,008.8m)
 */
class DistancesTest {

    private static final double DEGREE_OF_LATITUDE_M = 111_194.9;

    @Test
    @DisplayName("같은 좌표의 거리는 0이다")
    void samePointIsZero() {
        assertThat(Distances.metersBetween(37.5, 127.0, 37.5, 127.0)).isZero();
    }

    @Test
    @DisplayName("위도 1도 차이는 약 111km다")
    void oneDegreeOfLatitude() {
        assertThat(Distances.metersBetween(37.0, 127.0, 38.0, 127.0))
                .isCloseTo(DEGREE_OF_LATITUDE_M, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    @DisplayName("위도 0.01도 차이는 약 1.1km다")
    void smallLatitudeDifference() {
        assertThat(Distances.metersBetween(37.50, 127.0, 37.51, 127.0))
                .isCloseTo(DEGREE_OF_LATITUDE_M / 100, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    @DisplayName("경도 1도 차이는 적도에서 가장 크고 위도가 높을수록 줄어든다")
    void longitudeDistanceShrinksWithLatitude() {
        double atEquator = Distances.metersBetween(0.0, 0.0, 0.0, 1.0);
        double atSixty = Distances.metersBetween(60.0, 0.0, 60.0, 1.0);

        assertThat(atEquator).isCloseTo(DEGREE_OF_LATITUDE_M, org.assertj.core.data.Offset.offset(1.0));
        // cos(60도) = 0.5
        assertThat(atSixty).isCloseTo(atEquator / 2, org.assertj.core.data.Offset.offset(1.0));
    }

    @Test
    @DisplayName("방향이 바뀌어도 거리는 같다")
    void distanceIsSymmetric() {
        double forward = Distances.metersBetween(37.5, 127.0, 37.6, 127.1);
        double backward = Distances.metersBetween(37.6, 127.1, 37.5, 127.0);

        assertThat(forward).isEqualTo(backward);
    }

    @Test
    @DisplayName("지구 반대편 좌표도 수치 오차 없이 계산된다")
    void antipodalPointsAreFinite() {
        double meters = Distances.metersBetween(0.0, 0.0, 0.0, 180.0);

        // 지구 둘레의 절반 = π·R
        assertThat(meters).isCloseTo(Math.PI * 6_371_008.8, org.assertj.core.data.Offset.offset(1.0));
    }
}
