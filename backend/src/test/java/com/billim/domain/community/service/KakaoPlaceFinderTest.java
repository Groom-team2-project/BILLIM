package com.billim.domain.community.service;

import com.billim.global.json.JsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 공용 장소 후보 해석 (카카오 로컬 category.json, 공공기관 PO3).
 * 본문은 2026-10-23 실제 호출 결과를 그대로 썼다.
 */
class KakaoPlaceFinderTest {

    /** 역삼1동 중심 5km. 공공기관 분류에 거래 장소로 쓸 수 없는 곳이 섞여 온다 */
    private static final String MIXED = """
            {
              "meta": {"total_count": 15},
              "documents": [
                {
                  "place_name": "역삼1동주민센터",
                  "road_address_name": "서울 강남구 역삼로 지하 118",
                  "address_name": "서울 강남구 역삼동 718-35",
                  "x": "127.03299", "y": "37.49536"
                },
                {
                  "place_name": "주한산마리노공화국 명예총영사관",
                  "road_address_name": "서울 강남구 테헤란로 152",
                  "address_name": "서울 강남구 역삼동 737",
                  "x": "127.03611", "y": "37.50060"
                },
                {
                  "place_name": "국제셉테드연맹",
                  "road_address_name": "",
                  "address_name": "서울 강남구 역삼동 820",
                  "x": "127.04211", "y": "37.49210"
                },
                {
                  "place_name": "역삼119안전센터",
                  "road_address_name": "서울 강남구 논현로 317",
                  "address_name": "서울 강남구 역삼동 681",
                  "x": "127.03100", "y": "37.49800"
                }
              ]
            }
            """;

    /** 대관령면 5km. 산간은 3건뿐이다 */
    private static final String RURAL = """
            {
              "meta": {"total_count": 3},
              "documents": [
                {"place_name": "대관령119안전센터", "road_address_name": "강원 평창군 대관령면 횡계리",
                 "address_name": "강원 평창군 대관령면 횡계리 348", "x": "128.69", "y": "37.69"},
                {"place_name": "대관령면사무소", "road_address_name": "강원 평창군 대관령면 올림픽로 25",
                 "address_name": "강원 평창군 대관령면 횡계리 348-1", "x": "128.70", "y": "37.68"},
                {"place_name": "대관령파출소", "road_address_name": "강원 평창군 대관령면 올림픽로 30",
                 "address_name": "강원 평창군 대관령면 횡계리 350", "x": "128.71", "y": "37.70"}
              ]
            }
            """;

    private static final String EMPTY = """
            {"meta": {"total_count": 0}, "documents": []}
            """;

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://dapi.kakao.com");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final PlaceFinder finder = new KakaoPlaceFinder(builder.build(), new JsonCodec());

    @Test
    @DisplayName("거래 장소로 쓸 수 있는 공공기관만 남긴다. 명예총영사관·연맹은 빠진다")
    void keepsOnlyUsablePublicPlaces() {
        server.expect(requestTo(containsString("/v2/local/search/category.json")))
                .andRespond(withSuccess(MIXED, MediaType.APPLICATION_JSON));

        // 통과 후보가 2건이므로 limit을 2로 둔다. 모자라면 20km로 한 번 더 부른다
        List<PlaceCandidate> places = finder.findNear(37.4953, 127.0330, 2);

        assertThat(places).extracting(PlaceCandidate::name)
                .containsExactly("역삼1동주민센터", "역삼119안전센터");
        assertThat(places.getFirst().guide()).isEqualTo("서울 강남구 역삼로 지하 118");
    }

    @Test
    @DisplayName("도로명이 비면 지번 주소를 안내로 쓴다")
    void fallsBackToParcelAddress() {
        server.expect(requestTo(containsString("category.json")))
                .andRespond(withSuccess("""
                        {"documents": [
                          {"place_name": "역삼1동주민센터", "road_address_name": "",
                           "address_name": "서울 강남구 역삼동 718-35", "x": "127.03", "y": "37.49"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(finder.findNear(37.49, 127.03, 1))
                .singleElement()
                .extracting(PlaceCandidate::guide).isEqualTo("서울 강남구 역삼동 718-35");
    }

    @Test
    @DisplayName("5km에서 limit을 채우면 더 넓히지 않는다. 호출을 한 번으로 끝낸다")
    void stopsWhenLimitSatisfied() {
        server.expect(queryParam("category_group_code", "PO3"))
                .andExpect(queryParam("radius", "5000"))
                .andExpect(queryParam("sort", "distance"))
                .andRespond(withSuccess(RURAL, MediaType.APPLICATION_JSON));

        assertThat(finder.findNear(37.6908, 128.6967, 3)).hasSize(3);
        server.verify();   // 5km 한 번만 기대했으므로 20km를 불렀으면 실패한다
    }

    @Test
    @DisplayName("5km가 모자라면 20km로 한 번 더 넓힌다")
    void widensWhenNotEnough() {
        server.expect(queryParam("radius", "5000"))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));
        server.expect(queryParam("radius", "20000"))
                .andRespond(withSuccess(RURAL, MediaType.APPLICATION_JSON));

        assertThat(finder.findNear(37.6908, 128.6967, 3)).hasSize(3);
        server.verify();
    }

    @Test
    @DisplayName("같은 장소가 두 반경에서 겹쳐도 하나로 묶는다")
    void dedupesAcrossRadiusSteps() {
        server.expect(queryParam("radius", "5000"))
                .andRespond(withSuccess("""
                        {"documents": [
                          {"place_name": "대관령면사무소", "road_address_name": "강원 평창군 대관령면 올림픽로 25",
                           "address_name": "", "x": "128.70", "y": "37.68"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(queryParam("radius", "20000"))
                .andRespond(withSuccess(RURAL, MediaType.APPLICATION_JSON));

        assertThat(finder.findNear(37.6908, 128.6967, 4))
                .extracting(PlaceCandidate::name)
                .containsExactly("대관령면사무소", "대관령119안전센터", "대관령파출소");
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록")
    void returnsEmptyWhenNoCandidate() {
        server.expect(queryParam("radius", "5000"))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));
        server.expect(queryParam("radius", "20000"))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        assertThat(finder.findNear(0, 0, 3)).isEmpty();
    }

    @Test
    @DisplayName("연동 오류는 예외가 아니라 빈 목록이다. 장소 조회 자체는 성공해야 한다")
    void returnsEmptyOnError() {
        server.expect(requestTo(containsString("category.json")))
                .andRespond(withServerError());

        assertThat(finder.findNear(37.49, 127.03, 3)).isEmpty();
    }

    @Test
    @DisplayName("limit이 0이면 호출하지 않는다")
    void skipsWhenLimitZero() {
        assertThat(finder.findNear(37.49, 127.03, 0)).isEmpty();
        server.verify();
    }
}
