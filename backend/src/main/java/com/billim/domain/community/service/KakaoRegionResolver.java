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

/**
 * 카카오 로컬로 행정동을 찾는다. 좌표는 행정구역 변환, 이름은 주소 검색을 쓴다.
 *
 * <p>응답에는 법정동(B)과 행정동(H)이 함께 온다. 인증 기준은 행정동이라 H만 쓴다.
 * 법정동은 한 동이 여러 행정동으로 갈려 동네 단위와 맞지 않는다.
 *
 * <p>어떤 실패도 예외로 올리지 않는다. 호출 측이 반경 판정으로 내려갈 수 있어야 한다.
 * 좌표는 질의에만 쓰고 저장·로그에 남기지 않는다. (정책 및 상태 5 위치 인증)
 *
 * <p>호출 1회가 약 50ms라 결과를 캐시한다. 연동 실패는 캐시하지 않으므로 장애가 풀리면
 * 바로 복구된다. (성능 측정 E-커뮤니티운영-1차측정 5.1)
 */
public class KakaoRegionResolver implements RegionResolver {

    private static final Logger log = LoggerFactory.getLogger(KakaoRegionResolver.class);

    private static final String COORD_PATH = "/v2/local/geo/coord2regioncode.json";
    private static final String ADDRESS_PATH = "/v2/local/search/address.json";

    /** 행정동. 법정동은 "B" */
    private static final String ADMIN_REGION_TYPE = "H";

    /** 주소 검색 1회 요청 건수. 같은 행정동이 여러 주소로 나와 후보 수보다 넉넉해야 한다 */
    private static final int RESPONSE_SIZE = 30;

    private final RestClient restClient;
    private final JsonCodec jsonCodec;
    private final RegionCache cache;

    public KakaoRegionResolver(RestClient restClient, JsonCodec jsonCodec, RegionCache cache) {
        this.restClient = restClient;
        this.jsonCodec = jsonCodec;
        this.cache = cache;
    }

    @Override
    public Optional<Region> resolve(double latitude, double longitude) {
        Optional<Optional<Region>> cached = cache.getCoord(latitude, longitude);
        if (cached.isPresent()) {
            return cached.get();
        }
        try {
            String body = restClient.get()
                    .uri(builder -> builder.path(COORD_PATH)
                            .queryParam("x", longitude)   // 카카오는 x가 경도, y가 위도
                            .queryParam("y", latitude)
                            .build())
                    .retrieve()
                    .body(String.class);

            Optional<Region> region = toRegion(body);
            // 0건도 캐시한다. 바다·국외 좌표가 매번 외부로 나가지 않게
            cache.putCoord(latitude, longitude, region);
            return region;
        } catch (RuntimeException e) {
            // 좌표는 남기지 않는다. 실패했다는 사실과 원인만 남긴다
            log.warn("행정동 조회 실패. 반경 판정으로 대체한다: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * 주소 검색 결과에서 행정동을 모은다.
     *
     * <p>주소 검색은 지번·도로명 주소를 찾지만 결과마다 그 주소가 속한 행정동 코드(h_code)와
     * 이름을 함께 준다. 그래서 "역삼"처럼 일부만 입력해도 행정동 후보를 얻는다.
     * 같은 행정동이 여러 주소에서 나오므로 코드로 묶는다.
     */
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
            String body = restClient.get()
                    .uri(builder -> builder.path(ADDRESS_PATH)
                            .queryParam("query", keyword)
                            .queryParam("size", RESPONSE_SIZE)
                            .build())
                    .retrieve()
                    .body(String.class);

            List<Region> regions = toRegions(body, limit);
            // 0건도 캐시한다. 구·시 이름처럼 늘 행정동이 없는 검색어가 반복된다
            cache.putKeyword(keyword, limit, regions);
            return regions;
        } catch (RuntimeException e) {
            log.warn("행정동 이름 검색 실패. 등록된 동네만 보여준다: {}", e.toString());
            return List.of();
        }
    }

    private List<Region> toRegions(String body, int limit) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        KakaoAddressResponse response = jsonCodec.read(body, KakaoAddressResponse.class);
        if (response.documents() == null) {
            return List.of();
        }

        // 먼저 나온 주소의 좌표를 쓴다. 지번 단위 결과면 동 중심에서 조금 벗어나지만
        // 판정 기준은 행정동 코드이고 반경은 폴백이라 영향이 작다
        Map<String, Region> byCode = new LinkedHashMap<>();
        for (KakaoAddressDocument document : response.documents()) {
            Region region = document.toRegion();
            if (region != null) {
                byCode.putIfAbsent(region.code(), region);
            }
        }

        List<Region> regions = new ArrayList<>(byCode.values());
        return regions.size() <= limit ? regions : regions.subList(0, limit);
    }

    private Optional<Region> toRegion(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        KakaoRegionResponse response = jsonCodec.read(body, KakaoRegionResponse.class);
        List<KakaoRegionDocument> documents = response.documents();
        if (documents == null) {
            return Optional.empty();
        }

        return documents.stream()
                .filter(KakaoRegionDocument::isAdminRegion)
                .findFirst()
                .map(KakaoRegionDocument::toRegion);
    }

    /** 응답 필드가 늘어나도 읽을 수 있어야 하므로 {@link JsonCodec}로 느슨하게 읽는다 */
    private record KakaoRegionResponse(List<KakaoRegionDocument> documents) {
    }

    private record KakaoAddressResponse(List<KakaoAddressDocument> documents) {
    }

    /** 주소 검색 결과 한 건. 도로명만 있는 결과는 address가 비어 있다 */
    private record KakaoAddressDocument(KakaoAddress address) {

        Region toRegion() {
            return address == null ? null : address.toRegion();
        }
    }

    private record KakaoAddress(
            @JsonProperty("h_code") String hCode,
            @JsonProperty("region_1depth_name") String region1depthName,
            @JsonProperty("region_2depth_name") String region2depthName,
            @JsonProperty("region_3depth_h_name") String region3depthHName,
            double x,
            double y
    ) {

        Region toRegion() {
            if (hCode == null || hCode.length() != Region.CODE_LENGTH
                    || region3depthHName == null || region3depthHName.isBlank()) {
                return null;
            }
            String displayAddress = String.join(" ",
                    nullToEmpty(region1depthName), nullToEmpty(region2depthName), region3depthHName).strip();
            return new Region(hCode, region3depthHName, displayAddress, y, x);
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }

    private record KakaoRegionDocument(
            @JsonProperty("region_type") String regionType,
            String code,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("region_3depth_name") String region3depthName,
            double x,
            double y
    ) {

        boolean isAdminRegion() {
            return ADMIN_REGION_TYPE.equals(regionType)
                    && code != null && code.length() == Region.CODE_LENGTH
                    && region3depthName != null && !region3depthName.isBlank();
        }

        Region toRegion() {
            return new Region(code, region3depthName, addressName, y, x);
        }
    }
}
