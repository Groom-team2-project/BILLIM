package com.billim.domain.community.service;

import com.billim.domain.community.entity.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 읍면동 조회 캐시 (성능 측정 5.1 — 외부 호출 1회 약 50ms).
 */
class RegionCacheTest {

    private static final Region YEOKSAM = new Region(
            "11680101", "역삼동", "서울 강남구 역삼동", 37.4955, 127.0333);

    @Test
    @DisplayName("같은 좌표는 캐시에서 돌려준다")
    void hitsOnSameCoord() {
        RegionCache cache = new RegionCache(10, Duration.ofMinutes(30));
        cache.putCoord(37.5006, 127.0361, Optional.of(YEOKSAM));

        assertThat(cache.getCoord(37.5006, 127.0361)).contains(Optional.of(YEOKSAM));
    }

    @Test
    @DisplayName("11m 안쪽 흔들림은 같은 키로 묶는다. 측위는 같은 자리에서도 흔들린다")
    void groupsNearbyCoords() {
        assertThat(RegionCache.coordKey(37.50061, 127.03612))
                .isEqualTo(RegionCache.coordKey(37.50064, 127.03614));
        // 소수점 4자리가 달라지면 다른 키다
        assertThat(RegionCache.coordKey(37.5006, 127.0361))
                .isNotEqualTo(RegionCache.coordKey(37.5007, 127.0361));
    }

    @Test
    @DisplayName("없는 좌표는 빈 값")
    void missesOnUnknownCoord() {
        RegionCache cache = new RegionCache(10, Duration.ofMinutes(30));

        assertThat(cache.getCoord(37.5006, 127.0361)).isEmpty();
    }

    @Test
    @DisplayName("0건도 캐시한다. 바다·국외 좌표가 매번 외부로 나가면 안 된다")
    void cachesEmptyResult() {
        RegionCache cache = new RegionCache(10, Duration.ofMinutes(30));
        cache.putCoord(0, 0, Optional.empty());

        assertThat(cache.getCoord(0, 0)).contains(Optional.empty());
    }

    @Test
    @DisplayName("TTL이 지나면 다시 묻는다")
    void expiresAfterTtl() {
        RegionCache cache = new RegionCache(10, Duration.ZERO);
        cache.putCoord(37.5006, 127.0361, Optional.of(YEOKSAM));

        assertThat(cache.getCoord(37.5006, 127.0361)).isEmpty();
    }

    @Test
    @DisplayName("크기를 넘으면 가장 오래 쓰지 않은 항목이 빠진다")
    void evictsLeastRecentlyUsed() {
        RegionCache cache = new RegionCache(2, Duration.ofMinutes(30));
        cache.putCoord(1, 1, Optional.of(YEOKSAM));
        cache.putCoord(2, 2, Optional.of(YEOKSAM));
        cache.getCoord(1, 1);                              // 1을 최근 사용으로 올린다
        cache.putCoord(3, 3, Optional.of(YEOKSAM));        // 2가 빠져야 한다

        assertThat(cache.getCoord(1, 1)).isPresent();
        assertThat(cache.getCoord(3, 3)).isPresent();
        assertThat(cache.getCoord(2, 2)).isEmpty();
    }

    @Test
    @DisplayName("검색어 캐시는 limit까지 키에 넣는다")
    void separatesKeywordByLimit() {
        RegionCache cache = new RegionCache(10, Duration.ofMinutes(30));
        cache.putKeyword("역삼", 10, List.of(YEOKSAM));

        assertThat(cache.getKeyword("역삼", 10)).contains(List.of(YEOKSAM));
        assertThat(cache.getKeyword("역삼", 5)).isEmpty();
        assertThat(cache.getKeyword("정자", 10)).isEmpty();
    }
}
