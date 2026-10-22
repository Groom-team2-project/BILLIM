package com.billim.domain.community.service;

import com.billim.global.json.JsonCodec;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카카오 로컬 카테고리 검색으로 동네 주변 공공기관을 찾는다.
 *
 * <p>반경 5 km에서 시작한다. 실측(2026-10-23)으로 도시·읍·산간·제주 네 곳 모두
 * 5 km 한 번에 3건 이상 나오고, 가까운 순이라 그 동네의 주민센터·면사무소가 1순위로 온다.
 * 1 km부터 올리면 산간에서 0건이 나와 호출만 한 번 늘었다. 20 km는 섬처럼 5 km가
 * 비는 경우에만 쓴다 — 호출 수를 줄이는 것이 우선이다.
 *
 * <p>가까운 순으로 받되 두 번 걸러낸다. 공공기관 분류에는 명예영사관·협회처럼 거래 장소로
 * 쓸 수 없는 곳이 섞이고(이름 필터), 반경 안에는 옆 동네 것도 들어온다(경계 필터).
 * 역삼1동 중심 5 km에는 서초구 주민센터가 섞여 왔다.
 */
public class KakaoPlaceFinder implements PlaceFinder {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceFinder.class);

    private static final String CATEGORY_PATH = "/v2/local/search/category.json";

    /** 공공기관 */
    private static final String CATEGORY_PUBLIC = "PO3";

    /** 5 km로 대부분 끝난다. 모자랄 때만 카카오 상한인 20 km로 한 번 더 넓힌다 */
    private static final int[] RADIUS_STEPS = {5_000, 20_000};

    /** 한 번에 받는 건수. 걸러낸 뒤 남는 수가 필요한 만큼 되도록 넉넉히 받는다 */
    private static final int RESPONSE_SIZE = 15;

    /**
     * 거래 장소로 쓸 수 있는 공공기관 이름.
     *
     * <p>누구나 위치를 알고 낮에 사람이 있는 곳만 남긴다. 분류(PO3)만 믿으면
     * "주한산마리노공화국 명예총영사관"처럼 쓸 수 없는 곳이 들어온다.
     */
    private static final List<String> ALLOWED_NAMES = List.of(
            "주민센터", "행정복지센터", "동사무소", "읍사무소", "면사무소",
            "시청", "군청", "구청", "도청",
            "파출소", "지구대", "치안센터",
            "우체국", "도서관", "보건소", "소방서", "안전센터");

    private final RestClient restClient;
    private final JsonCodec jsonCodec;

    public KakaoPlaceFinder(RestClient restClient, JsonCodec jsonCodec) {
        this.restClient = restClient;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public List<PlaceCandidate> findNear(double latitude, double longitude,
                                         PlaceBoundary boundary, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        // 같은 이름이 여러 반경에서 반복되므로 이름으로 묶는다
        Map<String, PlaceCandidate> byName = new LinkedHashMap<>();
        for (int radius : RADIUS_STEPS) {
            try {
                for (PlaceCandidate candidate : search(latitude, longitude, radius)) {
                    if (boundary.containsAddress(candidate.address())) {
                        byName.putIfAbsent(candidate.name(), candidate);
                    }
                }
            } catch (RuntimeException e) {
                log.warn("공용 장소 후보 조회 실패. 장소를 채우지 않는다: radius={} 원인={}", radius, e.toString());
                return List.of();
            }
            if (countSameDistrict(byName.values(), boundary) >= limit) {
                break;
            }
        }
        return pick(byName.values(), boundary, limit);
    }

    private long countSameDistrict(Collection<PlaceCandidate> candidates, PlaceBoundary boundary) {
        return candidates.stream().filter(c -> boundary.isSameDistrict(c.address())).count();
    }

    /**
     * 같은 동네 후보를 먼저 채우고, 모자라면 같은 시군구 후보로 메운다.
     *
     * <p>행정동과 법정동 이름이 아예 다른 동네에서도 후보가 0건이 되지 않게 하려는 순서다.
     * 두 묶음 안에서는 가까운 순(응답 순서)을 유지한다.
     */
    private List<PlaceCandidate> pick(Collection<PlaceCandidate> candidates,
                                      PlaceBoundary boundary, int limit) {
        List<PlaceCandidate> picked = new ArrayList<>();
        for (PlaceCandidate candidate : candidates) {
            if (picked.size() >= limit) {
                break;
            }
            if (boundary.isSameDistrict(candidate.address())) {
                picked.add(candidate);
            }
        }
        for (PlaceCandidate candidate : candidates) {
            if (picked.size() >= limit) {
                break;
            }
            if (!boundary.isSameDistrict(candidate.address())) {
                picked.add(candidate);
            }
        }
        return List.copyOf(picked);
    }

    private List<PlaceCandidate> search(double latitude, double longitude, int radius) {
        String body = restClient.get()
                .uri(builder -> builder.path(CATEGORY_PATH)
                        .queryParam("category_group_code", CATEGORY_PUBLIC)
                        .queryParam("x", longitude)   // 카카오는 x가 경도
                        .queryParam("y", latitude)
                        .queryParam("radius", radius)
                        .queryParam("size", RESPONSE_SIZE)
                        .queryParam("sort", "distance")
                        .build())
                .retrieve()
                .body(String.class);

        if (body == null || body.isBlank()) {
            return List.of();
        }
        KakaoCategoryResponse response = jsonCodec.read(body, KakaoCategoryResponse.class);
        if (response.documents() == null) {
            return List.of();
        }
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (KakaoPlace place : response.documents()) {
            PlaceCandidate candidate = place.toCandidate();
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private static boolean isAllowed(String name) {
        return ALLOWED_NAMES.stream().anyMatch(name::contains);
    }

    private record KakaoCategoryResponse(List<KakaoPlace> documents) {
    }

    private record KakaoPlace(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("road_address_name") String roadAddressName,
            @JsonProperty("address_name") String addressName,
            String x,
            String y
    ) {

        PlaceCandidate toCandidate() {
            if (placeName == null || placeName.isBlank() || !isAllowed(placeName)) {
                return null;
            }
            BigDecimal latitude = parse(y);
            BigDecimal longitude = parse(x);
            if (latitude == null || longitude == null) {
                return null;
            }
            String guide = roadAddressName != null && !roadAddressName.isBlank()
                    ? roadAddressName
                    : addressName;
            return new PlaceCandidate(placeName.strip(), guide, addressName, latitude, longitude);
        }

        /** 좌표는 문자열로 온다 */
        private static BigDecimal parse(String value) {
            try {
                return value == null || value.isBlank() ? null : new BigDecimal(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
