package com.billim.domain.community.service;

import com.billim.domain.community.entity.Region;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 읍면동 조회 결과 캐시. 외부 호출 1회가 약 50ms라 반복 질의를 그대로 내보내면 안 된다.
 *
 * <p>읍면동 경계와 이름은 행정구역 개편이 있을 때만 바뀐다. 분 단위로 다시 물을 이유가 없어
 * TTL을 넉넉히 두고, 접근 순서 LRU로 크기를 묶는다.
 *
 * <p>찾지 못한 결과(빈 값)도 캐시한다. 바다·국외 좌표나 구 이름 검색처럼 늘 0건인 질의가
 * 매번 외부로 나가는 것을 막는다. 다만 연동 실패는 캐시하지 않는다 — 장애가 풀린 뒤에도
 * 빈 결과가 남으면 복구가 TTL만큼 늦어진다.
 */
class RegionCache {

    private record Entry<T>(T value, long expiresAt) {

        boolean isFresh(long now) {
            return expiresAt > now;
        }
    }

    private final Map<String, Entry<Optional<Region>>> coords;
    private final Map<String, Entry<List<Region>>> keywords;
    private final long ttlMillis;

    RegionCache(int maxSize, Duration ttl) {
        this.ttlMillis = ttl.toMillis();
        this.coords = lru(maxSize);
        this.keywords = lru(maxSize);
    }

    private static <V> Map<String, Entry<V>> lru(int maxSize) {
        return Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry<V>> eldest) {
                return size() > maxSize;
            }
        });
    }

    /**
     * 좌표 키. 소수점 4자리(약 11m)로 묶는다. 같은 자리에서 흔들리는 측위가 같은 키가 되고,
     * 읍면동 경계를 넘을 만큼 차이가 나면 다른 키가 된다.
     */
    static String coordKey(double latitude, double longitude) {
        return String.format("%.4f,%.4f", latitude, longitude);
    }

    Optional<Optional<Region>> getCoord(double latitude, double longitude) {
        return get(coords, coordKey(latitude, longitude));
    }

    void putCoord(double latitude, double longitude, Optional<Region> region) {
        put(coords, coordKey(latitude, longitude), region);
    }

    Optional<List<Region>> getKeyword(String keyword, int limit) {
        return get(keywords, keyword + "\u0000" + limit);
    }

    void putKeyword(String keyword, int limit, List<Region> regions) {
        put(keywords, keyword + "\u0000" + limit, List.copyOf(regions));
    }

    private <V> Optional<V> get(Map<String, Entry<V>> map, String key) {
        Entry<V> entry = map.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.isFresh(System.currentTimeMillis())) {
            map.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    private <V> void put(Map<String, Entry<V>> map, String key, V value) {
        map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }
}
