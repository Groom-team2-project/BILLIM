package com.billim.domain.community.service;

import com.billim.domain.community.entity.Region;
import com.billim.global.json.JsonCodec;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * 카카오 로컬 좌표 → 행정구역 변환으로 행정동을 찾는다.
 *
 * <p>응답에는 법정동(B)과 행정동(H)이 함께 온다. 인증 기준은 행정동이라 H만 쓴다.
 * 법정동은 한 동이 여러 행정동으로 갈려 동네 단위와 맞지 않는다.
 *
 * <p>어떤 실패도 예외로 올리지 않는다. 호출 측이 반경 판정으로 내려갈 수 있어야 한다.
 * 좌표는 질의에만 쓰고 저장·로그에 남기지 않는다. (정책 및 상태 5 위치 인증)
 */
public class KakaoRegionResolver implements RegionResolver {

    private static final Logger log = LoggerFactory.getLogger(KakaoRegionResolver.class);

    private static final String PATH = "/v2/local/geo/coord2regioncode.json";

    /** 행정동. 법정동은 "B" */
    private static final String ADMIN_REGION_TYPE = "H";

    private final RestClient restClient;
    private final JsonCodec jsonCodec;

    public KakaoRegionResolver(RestClient restClient, JsonCodec jsonCodec) {
        this.restClient = restClient;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public Optional<Region> resolve(double latitude, double longitude) {
        try {
            String body = restClient.get()
                    .uri(builder -> builder.path(PATH)
                            .queryParam("x", longitude)   // 카카오는 x가 경도, y가 위도
                            .queryParam("y", latitude)
                            .build())
                    .retrieve()
                    .body(String.class);

            return toRegion(body);
        } catch (RuntimeException e) {
            // 좌표는 남기지 않는다. 실패했다는 사실과 원인만 남긴다
            log.warn("행정동 조회 실패. 반경 판정으로 대체한다: {}", e.toString());
            return Optional.empty();
        }
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
