package com.billim.domain.community.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 주소를 동네 선택 단계로 쪼개기 (API 명세 E_073).
 *
 * <p>토큰 수가 지역마다 다르다. 네 단계가 다 있는 수원, 구가 없는 서울 자치구,
 * 시·군·구가 아예 없는 세종을 모두 받아야 한다.
 */
class RegionPathTest {

    @Test
    @DisplayName("구가 없는 지역은 세 토큰이고 구는 빈 문자열이다")
    void parsesThreeTokens() {
        RegionPath path = RegionPath.of("서울특별시 강남구 역삼1동");

        assertThat(path).isEqualTo(new RegionPath("서울특별시", "강남구", "", "역삼1동"));
        assertThat(path.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("구가 있는 시는 네 토큰이고 시와 구를 나눈다")
    void parsesFourTokens() {
        assertThat(RegionPath.of("경기도 수원시 영통구 영통1동"))
                .isEqualTo(new RegionPath("경기도", "수원시", "영통구", "영통1동"));
    }

    @Test
    @DisplayName("시·군·구가 없는 세종은 두 토큰이다")
    void parsesTwoTokens() {
        assertThat(RegionPath.of("세종특별자치시 조촌동"))
                .isEqualTo(new RegionPath("세종특별자치시", "", "", "조촌동"));
    }

    @Test
    @DisplayName("빈 시·군·구가 공백으로 남아도 토큰으로 세지 않는다")
    void collapsesRepeatedSpaces() {
        // 카카오는 region_2depth가 없으면 빈 문자열을 준다. 이어 붙이면 공백이 둘이 된다
        assertThat(RegionPath.of("세종특별자치시  조촌동"))
                .isEqualTo(new RegionPath("세종특별자치시", "", "", "조촌동"));
    }

    @Test
    @DisplayName("토큰이 다섯 이상이면 가운데를 모두 구로 본다. 시·도와 읍·면·동은 살린다")
    void joinsExtraTokensIntoGu() {
        assertThat(RegionPath.of("경기도 수원시 영통구 매탄 영통1동"))
                .isEqualTo(new RegionPath("경기도", "수원시", "영통구 매탄", "영통1동"));
    }

    @Test
    @DisplayName("단계로 쪼개지지 않는 주소는 비어 있다. 단계 조회에서 빠진다")
    void emptyForUnparsableAddress() {
        assertThat(RegionPath.of(null).isEmpty()).isTrue();
        assertThat(RegionPath.of("").isEmpty()).isTrue();
        assertThat(RegionPath.of("   ").isEmpty()).isTrue();
        assertThat(RegionPath.of("서울특별시").isEmpty()).isTrue();
    }
}
