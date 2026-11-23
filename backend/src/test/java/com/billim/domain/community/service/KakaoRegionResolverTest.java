package com.billim.domain.community.service;

import com.billim.domain.community.entity.Region;
import com.billim.global.json.JsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * 좌표 → 행정동 응답 해석 (API: 카카오 로컬 coord2regioncode).
 *
 * <p>법정동(B)과 행정동(H)이 함께 오는 응답에서 행정동만 골라야 하고,
 * 어떤 실패도 예외가 아니라 빈 값이어야 한다.
 */
class KakaoRegionResolverTest {

    /** 역삼1동. 법정동 행이 먼저 오는 실제 응답 순서를 그대로 둔다 */
    private static final String BOTH_TYPES = """
            {
              "meta": {"total_count": 2},
              "documents": [
                {
                  "region_type": "B", "code": "1168010100",
                  "address_name": "서울특별시 강남구 역삼동",
                  "region_1depth_name": "서울특별시", "region_2depth_name": "강남구",
                  "region_3depth_name": "역삼동", "region_4depth_name": "",
                  "x": 127.0333, "y": 37.4955
                },
                {
                  "region_type": "H", "code": "1168064000",
                  "address_name": "서울특별시 강남구 역삼1동",
                  "region_1depth_name": "서울특별시", "region_2depth_name": "강남구",
                  "region_3depth_name": "역삼1동", "region_4depth_name": "",
                  "x": 127.0361, "y": 37.5006
                }
              ]
            }
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    // 캐시가 응답을 가로채면 MockRestServiceServer 기대가 어긋난다. TTL 0으로 끈다
    private final RegionResolver resolver =
            new KakaoRegionResolver(builder.build(), new JsonCodec(), new RegionCache(0, Duration.ZERO));

    @Test
    @DisplayName("행정동(H)만 골라 읽는다")
    void readsAdminRegionOnly() {
        server.expect(requestTo(containsString("/v2/local/geo/coord2regioncode.json")))
                .andRespond(withSuccess(BOTH_TYPES, MediaType.APPLICATION_JSON));

        Optional<Region> region = resolver.resolve(37.5006, 127.0361);

        assertThat(region).isPresent();
        assertThat(region.get().code()).isEqualTo("1168064000");
        assertThat(region.get().name()).isEqualTo("역삼1동");
        assertThat(region.get().displayAddress()).isEqualTo("서울특별시 강남구 역삼1동");
        assertThat(region.get().latitude()).isEqualTo(37.5006);
        assertThat(region.get().longitude()).isEqualTo(127.0361);
        server.verify();
    }

    @Test
    @DisplayName("x는 경도, y는 위도로 질의한다")
    void sendsLongitudeAsX() {
        server.expect(queryParam("x", "127.0361"))
                .andExpect(queryParam("y", "37.5006"))
                .andRespond(withSuccess(BOTH_TYPES, MediaType.APPLICATION_JSON));

        resolver.resolve(37.5006, 127.0361);

        server.verify();
    }

    @Test
    @DisplayName("행정동이 없는 좌표는 빈 값. 바다·국외가 여기 해당한다")
    void returnsEmptyWithoutAdminRegion() {
        server.expect(requestTo(containsString("coord2regioncode")))
                .andRespond(withSuccess("""
                        {"meta": {"total_count": 0}, "documents": []}
                        """, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(0, 0)).isEmpty();
    }

    @Test
    @DisplayName("연동 오류는 예외가 아니라 빈 값이다. 반경 판정으로 내려가야 한다")
    void returnsEmptyOnError() {
        server.expect(requestTo(containsString("coord2regioncode")))
                .andRespond(withServerError());

        assertThat(resolver.resolve(37.5006, 127.0361)).isEmpty();
    }

    @Test
    @DisplayName("응답 필드가 늘어나도 읽는다")
    void ignoresUnknownFields() {
        server.expect(requestTo(containsString("coord2regioncode")))
                .andRespond(withSuccess("""
                        {
                          "meta": {"total_count": 1, "added_field": 1},
                          "documents": [
                            {
                              "region_type": "H", "code": "1168064000",
                              "address_name": "서울특별시 강남구 역삼1동",
                              "region_3depth_name": "역삼1동",
                              "x": 127.0361, "y": 37.5006,
                              "added_field": "무시"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(37.5006, 127.0361))
                .map(Region::code).contains("1168064000");
    }
    /** "역삼" 주소 검색. 같은 행정동이 여러 주소로 반복되고 도로명만 있는 결과가 섞인다 */
    private static final String ADDRESS_SEARCH = """
            {
              "meta": {"total_count": 4},
              "documents": [
                {
                  "address_name": "서울 강남구 역삼동 736",
                  "address": {
                    "address_name": "서울 강남구 역삼동 736",
                    "region_1depth_name": "서울", "region_2depth_name": "강남구",
                    "region_3depth_name": "역삼동", "region_3depth_h_name": "역삼1동",
                    "h_code": "1168064000", "b_code": "1168010100",
                    "x": 127.0361, "y": 37.5006
                  },
                  "road_address": null
                },
                {
                  "address_name": "서울 강남구 역삼동 737",
                  "address": {
                    "address_name": "서울 강남구 역삼동 737",
                    "region_1depth_name": "서울", "region_2depth_name": "강남구",
                    "region_3depth_name": "역삼동", "region_3depth_h_name": "역삼1동",
                    "h_code": "1168064000", "b_code": "1168010100",
                    "x": 127.0371, "y": 37.5016
                  },
                  "road_address": null
                },
                {
                  "address_name": "서울 강남구 역삼동 820",
                  "address": {
                    "address_name": "서울 강남구 역삼동 820",
                    "region_1depth_name": "서울", "region_2depth_name": "강남구",
                    "region_3depth_name": "역삼동", "region_3depth_h_name": "역삼2동",
                    "h_code": "1168065000", "b_code": "1168010100",
                    "x": 127.0421, "y": 37.4921
                  },
                  "road_address": null
                },
                {
                  "address_name": "서울 강남구 테헤란로 123",
                  "address": null,
                  "road_address": {"address_name": "서울 강남구 테헤란로 123"}
                }
              ]
            }
            """;

    @Test
    @DisplayName("주소 검색 결과를 행정동 코드로 묶는다. 같은 동 중복과 주소 없는 결과는 빠진다")
    void searchGroupsByAdminCode() {
        server.expect(requestTo(containsString("/v2/local/search/address.json")))
                .andRespond(withSuccess(ADDRESS_SEARCH, MediaType.APPLICATION_JSON));

        List<Region> regions = resolver.search("역삼", 10);

        assertThat(regions).extracting(Region::code).containsExactly("1168064000", "1168065000");
        assertThat(regions).extracting(Region::name).containsExactly("역삼1동", "역삼2동");
        assertThat(regions.getFirst().displayAddress()).isEqualTo("서울 강남구 역삼1동");
        // 먼저 나온 주소의 좌표를 쓴다
        assertThat(regions.getFirst().latitude()).isEqualTo(37.5006);
        server.verify();
    }

    @Test
    @DisplayName("limit으로 후보 수를 자른다")
    void searchRespectsLimit() {
        server.expect(requestTo(containsString("address.json")))
                .andRespond(withSuccess(ADDRESS_SEARCH, MediaType.APPLICATION_JSON));

        assertThat(resolver.search("역삼", 1)).extracting(Region::code).containsExactly("1168064000");
    }

    @Test
    @DisplayName("검색어가 비면 지도를 부르지 않는다")
    void searchSkipsBlankKeyword() {
        assertThat(resolver.search("  ", 10)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("연동 오류는 빈 목록이다. 등록된 동네만 보여주면 된다")
    void searchReturnsEmptyOnError() {
        server.expect(requestTo(containsString("address.json")))
                .andRespond(withServerError());

        assertThat(resolver.search("역삼", 10)).isEmpty();
    }
}
