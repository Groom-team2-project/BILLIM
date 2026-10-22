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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * VWorld 응답 해석. 본문은 2026-10-22 실제 호출 결과를 그대로 썼다.
 */
class VworldRegionResolverTest {

    /** 역삼동 좌표. 지번과 도로명이 함께 온다 */
    private static final String GET_ADDRESS_BOTH = """
            {
              "response": {
                "service": {"name": "address", "operation": "getAddress"},
                "status": "OK",
                "result": [
                  {
                    "zipcode": "06232", "type": "parcel",
                    "text": "서울특별시 강남구 역삼동 804",
                    "structure": {
                      "level0": "대한민국", "level1": "서울특별시", "level2": "강남구", "level3": "",
                      "level4L": "역삼동", "level4LC": "1168010100",
                      "level4A": "역삼1동", "level4AC": "1168064000",
                      "level5": "804도", "detail": ""
                    }
                  },
                  {
                    "zipcode": "06236", "type": "road",
                    "text": "서울특별시 강남구 테헤란로 156 (역삼동,역삼역)",
                    "structure": {
                      "level0": "대한민국", "level1": "서울특별시", "level2": "강남구", "level3": "역삼동",
                      "level4L": "테헤란로", "level4LC": "3122010",
                      "level4A": "역삼1동", "level4AC": "1168064000",
                      "level5": "156", "detail": "역삼역"
                    }
                  }
                ]
              }
            }
            """;

    /** 양평읍 좌표. 법정동 코드 뒤 2자리가 리(21)다 */
    private static final String GET_ADDRESS_RURAL = """
            {
              "response": {
                "status": "OK",
                "result": [
                  {
                    "zipcode": "12500", "type": "parcel",
                    "text": "경기도 양평군 양평읍 양근리 448-8",
                    "structure": {
                      "level1": "경기도", "level2": "양평군", "level3": "",
                      "level4L": "양평읍", "level4LC": "4183025021",
                      "level4A": "양평읍", "level4AC": "4183025000",
                      "level5": "448-8", "detail": ""
                    }
                  }
                ]
              }
            }
            """;

    /** 행정구역 L4 검색. point의 x, y는 문자열이다 */
    private static final String SEARCH_DISTRICT = """
            {
              "response": {
                "status": "OK",
                "record": {"total": "4", "current": "4"},
                "result": {
                  "items": [
                    {
                      "id": "41135103", "title": "경기도 성남시 분당구 정자동",
                      "point": {"x": "127.1115403", "y": "37.36145085"}
                    },
                    {
                      "id": "41111130", "title": "경기도 수원시 장안구 정자동",
                      "point": {"x": "126.991288", "y": "37.3022"}
                    }
                  ]
                }
              }
            }
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.vworld.kr");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    // 캐시가 응답을 가로채면 MockRestServiceServer 기대가 어긋난다. TTL 0으로 끈다
    private final RegionResolver resolver =
            new VworldRegionResolver(builder.build(), new JsonCodec(), new RegionCache(0, Duration.ZERO), "test-key");

    @Test
    @DisplayName("지번(parcel) 결과만 읽고 법정동 코드 앞 8자리를 쓴다")
    void readsParcelResultOnly() {
        server.expect(requestTo(containsString("/req/address")))
                .andRespond(withSuccess(GET_ADDRESS_BOTH, MediaType.APPLICATION_JSON));
        // 대표 좌표를 찾는 두 번째 호출. 코드가 다르면 좌표 없이 돌려준다
        server.expect(manyTimes(), requestTo(containsString("/req/search")))
                .andRespond(withSuccess(SEARCH_DISTRICT, MediaType.APPLICATION_JSON));

        Optional<Region> region = resolver.resolve(37.5006, 127.0361);

        assertThat(region).isPresent();
        assertThat(region.get().code()).isEqualTo("11680101");   // 1168010100의 앞 8자리
        assertThat(region.get().name()).isEqualTo("역삼동");
        assertThat(region.get().displayAddress()).isEqualTo("서울특별시 강남구 역삼동");
    }

    @Test
    @DisplayName("도로명 결과를 읍면동으로 쓰지 않는다. level4L이 도로명이고 level4LC가 도로 코드다")
    void ignoresRoadResult() {
        server.expect(requestTo(containsString("/req/address")))
                .andRespond(withSuccess(GET_ADDRESS_BOTH, MediaType.APPLICATION_JSON));
        server.expect(manyTimes(), requestTo(containsString("/req/search")))
                .andRespond(withSuccess(SEARCH_DISTRICT, MediaType.APPLICATION_JSON));

        // 도로 코드 3122010이 섞이면 이름이 "테헤란로"가 된다
        assertThat(resolver.resolve(37.5006, 127.0361))
                .map(Region::name).contains("역삼동");
    }

    @Test
    @DisplayName("농촌은 리가 아니라 읍 단위로 잡는다")
    void keepsEupMyeonUnitInRuralArea() {
        server.expect(requestTo(containsString("/req/address")))
                .andRespond(withSuccess(GET_ADDRESS_RURAL, MediaType.APPLICATION_JSON));
        server.expect(manyTimes(), requestTo(containsString("/req/search")))
                .andRespond(withSuccess(SEARCH_DISTRICT, MediaType.APPLICATION_JSON));

        Optional<Region> region = resolver.resolve(37.4917, 127.4875);

        assertThat(region).isPresent();
        assertThat(region.get().code()).isEqualTo("41830250");   // 4183025021에서 리(21)를 뗀다
        assertThat(region.get().name()).isEqualTo("양평읍");
    }

    @Test
    @DisplayName("결과 없음(NOT_FOUND)은 오류가 아니라 빈 값이다")
    void returnsEmptyOnNotFound() {
        server.expect(requestTo(containsString("/req/address")))
                .andRespond(withSuccess("""
                        {"response": {"status": "NOT_FOUND"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(0, 0)).isEmpty();
    }

    @Test
    @DisplayName("인증키 오류도 예외가 아니라 빈 값이다. 반경 판정으로 내려가야 한다")
    void returnsEmptyOnKeyError() {
        server.expect(requestTo(containsString("/req/address")))
                .andRespond(withSuccess("""
                        {"response": {"status": "ERROR",
                          "error": {"level": "2", "code": "INVALID_KEY", "text": "등록되지 않은 인증키입니다."}}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve(37.5006, 127.0361)).isEmpty();
    }

    @Test
    @DisplayName("연동 오류도 빈 값이다")
    void returnsEmptyOnServerError() {
        server.expect(requestTo(containsString("/req/address")))
                .andRespond(withServerError());

        assertThat(resolver.resolve(37.5006, 127.0361)).isEmpty();
    }

    @Test
    @DisplayName("이름 검색은 L4 id를 코드로 쓰고 point의 문자열 좌표를 읽는다")
    void searchReadsDistrictItems() {
        server.expect(requestTo(containsString("/req/search")))
                .andRespond(withSuccess(SEARCH_DISTRICT, MediaType.APPLICATION_JSON));

        List<Region> regions = resolver.search("정자", 10);

        assertThat(regions).extracting(Region::code).containsExactly("41135103", "41111130");
        assertThat(regions).extracting(Region::name).containsExactly("정자동", "정자동");
        assertThat(regions.getFirst().displayAddress()).isEqualTo("경기도 성남시 분당구 정자동");
        assertThat(regions.getFirst().latitude()).isEqualTo(37.36145085);
        assertThat(regions.getFirst().longitude()).isEqualTo(127.1115403);
    }

    @Test
    @DisplayName("행정구역 검색은 category=L4로 읍면동만 찾는다")
    void searchesDistrictCategory() {
        server.expect(queryParam("type", "district"))
                .andExpect(queryParam("category", "L4"))
                .andRespond(withSuccess(SEARCH_DISTRICT, MediaType.APPLICATION_JSON));

        resolver.search("정자", 10);

        server.verify();
    }

    @Test
    @DisplayName("limit으로 후보 수를 자른다")
    void searchRespectsLimit() {
        server.expect(requestTo(containsString("/req/search")))
                .andRespond(withSuccess(SEARCH_DISTRICT, MediaType.APPLICATION_JSON));

        assertThat(resolver.search("정자", 1)).extracting(Region::code).containsExactly("41135103");
    }

    @Test
    @DisplayName("검색어가 비면 호출하지 않는다")
    void searchSkipsBlankKeyword() {
        assertThat(resolver.search("  ", 10)).isEmpty();
        server.verify();
    }
}
