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
 * 카카오 로컬로 동네(법정동 읍면동)를 찾는다. 좌표는 행정구역 변환, 이름은 주소 검색을 쓴다.
 *
 * <p>응답에는 법정동(B)과 행정동(H)이 함께 온다. 인증 기준은 법정동이라 B만 쓴다.
 * 행정동은 인구에 따라 분동·통합이 잦아 코드가 자주 바뀐다.
 *
 * <p>법정동 코드 10자리에서 앞 8자리(읍면동)만 쓴다. 뒤 2자리는 리라서, 그대로 두면
 * 농촌이 리 단위로 쪼개진다 (양평읍 양근리, 대관령면 차항리). 앞 8자리는
 * VWorld 행정구역 검색의 L4 id와 같은 값이다.
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

    /** 법정동. 행정동은 "H" */
    private static final String LEGAL_REGION_TYPE = "B";

    /** 주소 검색 1회 요청 건수. 같은 읍면동이 여러 주소로 나와 후보 수보다 넉넉해야 한다 */
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
            log.warn("읍면동 조회 실패. 반경 판정으로 대체한다: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * 주소 검색 결과에서 읍면동을 모은다.
     *
     * <p>주소 검색은 지번·도로명 주소를 찾지만 결과마다 그 주소가 속한 법정동 코드(b_code)와
     * 이름을 함께 준다. 그래서 "역삼"처럼 일부만 입력해도 후보를 얻는다.
     * 같은 읍면동이 여러 주소에서 나오므로 코드로 묶는다.
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
            // 0건도 캐시한다. 구·시 이름처럼 늘 읍면동이 없는 검색어가 반복된다
            cache.putKeyword(keyword, limit, regions);
            return regions;
        } catch (RuntimeException e) {
            log.warn("읍면동 이름 검색 실패. 등록된 동네만 보여준다: {}", e.toString());
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
        // 판정 기준은 읍면동 코드이고 반경은 폴백이라 영향이 작다
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
                .filter(KakaoRegionDocument::isLegalDistrict)
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
            @JsonProperty("b_code") String bCode,
            @JsonProperty("region_1depth_name") String region1depthName,
            @JsonProperty("region_2depth_name") String region2depthName,
            @JsonProperty("region_3depth_name") String region3depthName,
            double x,
            double y
    ) {

        /** 행정동만 담긴 결과는 b_code가 비어 있다. 그 행은 건너뛴다 */
        Region toRegion() {
            if (bCode == null || bCode.length() != Region.SOURCE_CODE_LENGTH
                    || region3depthName == null || region3depthName.isBlank()) {
                return null;
            }
            return new Region(
                    Region.toDistrictCode(bCode), region3depthName,
                    joinAddress(region1depthName, region2depthName, region3depthName), y, x);
        }
    }

    /** 빈 조각은 건너뛰고 공백 하나로 잇는다 */
    private static String joinAddress(String sido, String sigungu, String district) {
        return Stream.of(sido, sigungu, district)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    private record KakaoRegionDocument(
            @JsonProperty("region_type") String regionType,
            String code,
            @JsonProperty("region_1depth_name") String region1depthName,
            @JsonProperty("region_2depth_name") String region2depthName,
            @JsonProperty("region_3depth_name") String region3depthName,
            double x,
            double y
    ) {

        boolean isLegalDistrict() {
            return LEGAL_REGION_TYPE.equals(regionType)
                    && code != null && code.length() == Region.SOURCE_CODE_LENGTH
                    && region3depthName != null && !region3depthName.isBlank();
        }

        /** address_name은 리까지 담고 있어 쓰지 않는다. 읍면동까지 직접 이어 붙인다 */
        Region toRegion() {
            return new Region(
                    Region.toDistrictCode(code), region3depthName,
                    joinAddress(region1depthName, region2depthName, region3depthName), y, x);
        }
    }
}
