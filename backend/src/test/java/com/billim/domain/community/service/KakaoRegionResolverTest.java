package com.billim.domain.community.service;

import com.billim.domain.community.entity.Region;
import com.billim.global.json.JsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
    private final RegionResolver resolver = new KakaoRegionResolver(builder.build(), new JsonCodec());

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
}
