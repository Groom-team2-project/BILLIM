package com.billim.domain.community.service;

import com.billim.domain.community.entity.Region;
import com.billim.global.json.JsonCodec;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * VWorld 오픈API로 읍면동을 찾는다. 좌표는 주소 변환(getAddress), 이름은 행정구역 검색(search)을 쓴다.
 *
 * <p>기본 제공자는 카카오다(약 40ms 빠름). 쓰려는 앱에 카카오맵 권한이 없거나
 * 구·시 이름 검색이 필요하면 이 구현으로 바꾼다. VWorld 행정구역 검색의 L4 id는
 * 법정동 코드 앞 8자리로, 저장하는 region_code와 같은 값이다. 그래서 제공자를 바꿔도
 * 기존 코드가 그대로 유효하다. (도메인 가이드 결정 — 법정동 위치 인증)
 *
 * <p>getAddress는 좌표가 어느 주소인지만 답하고 그 읍면동의 대표 좌표는 주지 않는다.
 * 자동 등록에는 동네 중심 좌표가 필요하므로, 코드를 얻은 뒤 같은 읍면동을 행정구역 검색으로
 * 한 번 더 찾아 대표 좌표를 받는다. 요청자의 좌표를 중심으로 쓰지 않는다 — 거리 정렬이
 * 한 사람의 위치에 끌려가고, 개인 좌표를 저장하지 않는다는 원칙에도 어긋난다.
 *
 * <p>어떤 실패도 예외로 올리지 않는다. 호출 측이 반경 판정으로 내려갈 수 있어야 한다.
 */
public class VworldRegionResolver implements RegionResolver {

    private static final Logger log = LoggerFactory.getLogger(VworldRegionResolver.class);

    private static final String ADDRESS_PATH = "/req/address";
    private static final String SEARCH_PATH = "/req/search";

    /** 읍면동 */
    private static final String CATEGORY_DISTRICT = "L4";

    /** 결과가 없다는 뜻이며 오류가 아니다 */
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";
    private static final String STATUS_OK = "OK";

    /**
     * 지번 주소 결과만 쓴다.
     *
     * <p>도로명 결과는 같은 필드에 도로 정보를 담는다. 역삼동 좌표의 도로명 결과는
     * {@code level4L="테헤란로"}, {@code level4LC="3122010"}(도로 코드)이다.
     * 읍면동으로 착각하면 엉뚱한 코드가 동네로 등록된다.
     */
    private static final String RESULT_TYPE_PARCEL = "parcel";

    /** 같은 이름의 읍면동이 여러 지역에 있어 넉넉히 받아 코드로 고른다 */
    private static final int SEARCH_SIZE = 30;

    private final RestClient restClient;
    private final JsonCodec jsonCodec;
    private final RegionCache cache;
    private final String key;

    public VworldRegionResolver(RestClient restClient, JsonCodec jsonCodec, RegionCache cache, String key) {
        this.restClient = restClient;
        this.jsonCodec = jsonCodec;
        this.cache = cache;
        this.key = key;
    }

    @Override
    public Optional<Region> resolve(double latitude, double longitude) {
        Optional<Optional<Region>> cached = cache.getCoord(latitude, longitude);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            Optional<Region> region = toRegion(getAddress(latitude, longitude));
            cache.putCoord(latitude, longitude, region);
            return region;
        } catch (RuntimeException e) {
            log.warn("읍면동 조회 실패. 반경 판정으로 대체한다: {}", e.toString());
            return Optional.empty();
        }
    }

    @Override
    public List<Region> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank() || limit <= 0) {
            return List.of();
        }
        Optional<List<Region>> cached = cache.getKeyword(keyword, limit);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            List<Region> regions = searchDistrict(keyword);
            List<Region> capped = regions.size() <= limit ? regions : List.copyOf(regions.subList(0, limit));
            cache.putKeyword(keyword, limit, capped);
            return capped;
        } catch (RuntimeException e) {
            log.warn("읍면동 이름 검색 실패. 등록된 동네만 보여준다: {}", e.toString());
            return List.of();
        }
    }

    /** 좌표 → 주소. 지번과 도로명을 함께 받아 법정동 코드가 담긴 결과를 쓴다 */
    private VworldAddressResponse getAddress(double latitude, double longitude) {
        String body = restClient.get()
                .uri(builder -> builder.path(ADDRESS_PATH)
                        .queryParam("service", "address")
                        .queryParam("request", "getAddress")
                        .queryParam("version", "2.0")
                        .queryParam("crs", "epsg:4326")
                        .queryParam("point", longitude + "," + latitude)
                        .queryParam("type", "both")
                        .queryParam("format", "json")
                        .queryParam("key", key)
                        .build())
                .retrieve()
                .body(String.class);

        return read(body, VworldAddressResponse.class);
    }

    /**
     * 주소 변환 결과에서 읍면동을 뽑고 대표 좌표를 채운다.
     *
     * <p>대표 좌표를 못 찾으면 좌표 없는 결과를 돌려준다. 인증 판정은 코드만 쓰므로 그대로
     * 동작하고 동네 자동 등록만 건너뛴다.
     */
    private Optional<Region> toRegion(VworldAddressResponse response) {
        VworldAddressBody body = response.response();
        if (body == null || STATUS_NOT_FOUND.equals(body.status())) {
            return Optional.empty();
        }
        requireOk(body.status(), body.error(), "getAddress");

        if (body.result() == null) {
            return Optional.empty();
        }
        return body.result().stream()
                .filter(VworldAddressResult::isParcel)
                .map(VworldAddressResult::structure)
                .filter(structure -> structure != null && structure.hasDistrictCode())
                .findFirst()
                .map(this::withCenter);
    }

    /** 같은 읍면동을 이름으로 다시 찾아 대표 좌표를 받는다. 캐시가 있어 동마다 한 번이면 끝난다 */
    private Region withCenter(VworldStructure structure) {
        String code = structure.districtCode();
        String name = structure.level4L();
        String displayAddress = joinAddress(structure.level1(), structure.level2(), name);
        Region withoutCenter = new Region(code, name, displayAddress, 0, 0);

        try {
            return findByCode(searchDistrict(displayAddress), code)
                    .or(() -> findByCode(searchDistrict(name), code))
                    .orElse(withoutCenter);
        } catch (RuntimeException e) {
            // 대표 좌표만 못 받은 것이다. 코드는 유효하므로 인증 판정은 계속된다
            log.warn("읍면동 대표 좌표 조회 실패. 자동 등록은 건너뛴다: regionCode={} 원인={}", code, e.toString());
            return withoutCenter;
        }
    }

    private Optional<Region> findByCode(List<Region> candidates, String code) {
        return candidates.stream().filter(candidate -> code.equals(candidate.code())).findFirst();
    }

    /**
     * 행정구역(L4) 이름 검색. 같은 읍면동은 코드로 묶는다.
     *
     * <p>대표 좌표를 채울 때도 이 검색을 쓴다. 인증마다 좌표가 조금씩 달라 좌표 캐시는 비껴가지만
     * 지명은 같으므로, 여기서 캐시해 같은 동네의 두 번째 호출을 없앤다.
     */
    private List<Region> searchDistrict(String query) {
        Optional<List<Region>> cached = cache.getKeyword(query, SEARCH_SIZE);
        if (cached.isPresent()) {
            return cached.get();
        }
        String body = restClient.get()
                .uri(builder -> builder.path(SEARCH_PATH)
                        .queryParam("service", "search")
                        .queryParam("request", "search")
                        .queryParam("version", "2.0")
                        .queryParam("type", "district")
                        .queryParam("category", CATEGORY_DISTRICT)
                        .queryParam("query", query)
                        .queryParam("size", SEARCH_SIZE)
                        .queryParam("page", 1)
                        .queryParam("crs", "EPSG:4326")
                        .queryParam("format", "json")
                        .queryParam("key", key)
                        .build())
                .retrieve()
                .body(String.class);

        VworldSearchBody response = read(body, VworldSearchResponse.class).response();
        if (response == null || STATUS_NOT_FOUND.equals(response.status())) {
            cache.putKeyword(query, SEARCH_SIZE, List.of());
            return List.of();
        }
        requireOk(response.status(), response.error(), "search");

        if (response.result() == null || response.result().items() == null) {
            cache.putKeyword(query, SEARCH_SIZE, List.of());
            return List.of();
        }

        Map<String, Region> byCode = new LinkedHashMap<>();
        for (VworldSearchItem item : response.result().items()) {
            Region region = item.toRegion();
            if (region != null) {
                byCode.putIfAbsent(region.code(), region);
            }
        }
        List<Region> regions = new ArrayList<>(byCode.values());
        cache.putKeyword(query, SEARCH_SIZE, regions);
        return regions;
    }

    private void requireOk(String status, VworldError error, String operation) {
        if (!STATUS_OK.equals(status)) {
            throw new IllegalStateException("VWorld " + operation + " status=" + status
                    + " " + (error == null ? "" : error.text()));
        }
    }

    private <T> T read(String body, Class<T> type) {
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("VWorld 응답이 비어 있습니다.");
        }
        return jsonCodec.read(body, type);
    }

    /** 빈 조각은 건너뛰고 공백 하나로 잇는다 */
    private static String joinAddress(String sido, String sigungu, String district) {
        return Stream.of(sido, sigungu, district)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    // --- 응답 모델. 모르는 필드는 JsonCodec이 무시한다 ---
    // getAddress는 result가 배열, search는 객체라 응답 타입을 따로 둔다

    private record VworldAddressResponse(VworldAddressBody response) {
    }

    private record VworldAddressBody(String status, VworldError error, List<VworldAddressResult> result) {
    }

    private record VworldSearchResponse(VworldSearchBody response) {
    }

    private record VworldSearchBody(String status, VworldError error, VworldSearchResult result) {
    }

    private record VworldError(String code, String text) {
    }

    private record VworldAddressResult(String type, String text, VworldStructure structure) {

        boolean isParcel() {
            return RESULT_TYPE_PARCEL.equals(type);
        }
    }

    /** getAddress의 행정구역 분해. level4L은 법정 읍면동 이름, level4LC는 그 코드 10자리 */
    private record VworldStructure(
            String level1,
            String level2,
            String level4L,
            @JsonProperty("level4LC") String level4LC
    ) {

        boolean hasDistrictCode() {
            return districtCode() != null && level4L != null && !level4L.isBlank();
        }

        String districtCode() {
            return Region.toDistrictCode(level4LC);
        }
    }

    private record VworldSearchResult(List<VworldSearchItem> items) {
    }

    /** 행정구역 검색 1건. id는 이미 법정동 앞 8자리이고 title은 전체 지명이다 */
    private record VworldSearchItem(String id, String title, VworldPoint point) {

        Region toRegion() {
            if (id == null || id.length() != Region.CODE_LENGTH || title == null || title.isBlank()) {
                return null;
            }
            String full = title.strip();
            String name = full.substring(full.lastIndexOf(' ') + 1);
            return new Region(id, name, full,
                    point == null ? 0 : point.latitude(),
                    point == null ? 0 : point.longitude());
        }
    }

    /** point의 x, y는 문자열로 온다 */
    private record VworldPoint(String x, String y) {

        double latitude() {
            return parse(y);
        }

        double longitude() {
            return parse(x);
        }

        private static double parse(String value) {
            try {
                return value == null ? 0 : Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
