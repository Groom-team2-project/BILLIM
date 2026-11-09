package com.billim.domain.community.controller;

import com.billim.domain.community.dto.CommunityPageResponse;
import com.billim.domain.community.dto.CommunityResponse;
import com.billim.domain.community.dto.MembershipResponse;
import com.billim.domain.community.dto.NearbyCommunitiesResponse;
import com.billim.domain.community.dto.PlaceListResponse;
import com.billim.domain.community.dto.PlaceResponse;
import com.billim.domain.community.dto.RegionLevel;
import com.billim.domain.community.dto.RegionOptionsResponse;
import com.billim.domain.community.dto.RegionScope;
import com.billim.domain.community.dto.VerifyLocationRequest;
import com.billim.domain.community.service.CommunityPlaceService;
import com.billim.domain.community.service.CommunityQueryService;
import com.billim.domain.community.service.CommunityVerificationService;
import com.billim.domain.member.entity.MemberRole;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 동네 API 계약 (API 명세 4장). 운영 Security 설정은 건드리지 않는다 */
@WebMvcTest(controllers = CommunityController.class, properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
class CommunityControllerTest {

    private static final long ME = 7L;
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";
    private static final String LOCATION_BODY =
            "{\"latitude\":37.5,\"longitude\":127.0,\"accuracyMeters\":12.5}";

    @Autowired MockMvc mockMvc;
    @MockitoBean CommunityQueryService queryService;
    @MockitoBean CommunityVerificationService verificationService;
    @MockitoBean CommunityPlaceService placeService;

    private static Authentication login() {
        LoginMember member = new LoginMember(ME, MemberRole.USER);
        return new OAuth2AuthenticationToken(member, member.getAuthorities(), "kakao");
    }

    private static CommunityResponse community() {
        return new CommunityResponse("100", "새솔마을 5단지", "시흥시 정왕동 일대",
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), 500, 12);
    }

    @Test
    @DisplayName("동네 검색은 ID를 문자열로 내리고 keyword를 서비스에 전달한다")
    void searchPassesKeyword() throws Exception {
        when(queryService.search(eq("새솔"), any(RegionScope.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new CommunityPageResponse(List.of(community()), 0, 20, 1, false));

        mockMvc.perform(get("/api/v1/communities")
                        .param("keyword", "새솔")
                        .with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("100"))
                .andExpect(jsonPath("$.items[0].verificationRadiusM").value(500))
                .andExpect(jsonPath("$.items[0].verifiedMemberCount").value(12));

        verify(queryService).search("새솔", RegionScope.ALL, false, 0, 20);
    }

    @Test
    @DisplayName("keyword 없이도 검색할 수 있다. 위치는 필요하지 않다")
    void searchWithoutKeyword() throws Exception {
        when(queryService.search(any(), any(RegionScope.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new CommunityPageResponse(List.of(), 0, 20, 0, false));

        mockMvc.perform(get("/api/v1/communities").with(authentication(login())))
                .andExpect(status().isOk());

        verify(queryService).search(null, RegionScope.ALL, false, 0, 20);
    }

    @Test
    @DisplayName("고른 지역 단계를 범위로 서비스에 전달한다")
    void searchPassesRegionScope() throws Exception {
        when(queryService.search(any(), any(RegionScope.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new CommunityPageResponse(List.of(), 0, 20, 0, false));

        mockMvc.perform(get("/api/v1/communities")
                        .param("sido", "경기도")
                        .param("sigungu", "수원시")
                        .param("gu", "영통구")
                        .param("dong", "영통1동")
                        .with(authentication(login())))
                .andExpect(status().isOk());

        verify(queryService).search(
                null, new RegionScope("경기도", "수원시", "영통구", "영통1동"), false, 0, 20);
    }

    @Test
    @DisplayName("hasMembers를 주면 가입자가 있는 동네만 요청한다. 기본값은 끈 상태다")
    void searchPassesHasMembers() throws Exception {
        when(queryService.search(any(), any(RegionScope.class), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new CommunityPageResponse(List.of(), 0, 20, 0, false));

        mockMvc.perform(get("/api/v1/communities")
                        .param("hasMembers", "true")
                        .with(authentication(login())))
                .andExpect(status().isOk());

        verify(queryService).search(null, RegionScope.ALL, true, 0, 20);
    }

    @Test
    @DisplayName("단계 옵션은 level과 상위 단계를 서비스에 전달한다")
    void regionOptionsPassesLevelAndScope() throws Exception {
        when(queryService.findRegionOptions(any(), any(RegionScope.class), anyBoolean()))
                .thenReturn(new RegionOptionsResponse(RegionLevel.SIGUNGU, List.of("강남구", "마포구")));

        mockMvc.perform(get("/api/v1/communities/regions")
                        .param("level", "SIGUNGU")
                        .param("sido", "서울특별시")
                        .with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("SIGUNGU"))
                .andExpect(jsonPath("$.items[0]").value("강남구"));

        verify(queryService).findRegionOptions(
                RegionLevel.SIGUNGU, new RegionScope("서울특별시", "", "", ""), false);
    }

    @Test
    @DisplayName("단계 옵션도 hasMembers를 그대로 전달한다")
    void regionOptionsPassesHasMembers() throws Exception {
        when(queryService.findRegionOptions(any(), any(RegionScope.class), anyBoolean()))
                .thenReturn(new RegionOptionsResponse(RegionLevel.SIDO, List.of("서울특별시")));

        mockMvc.perform(get("/api/v1/communities/regions")
                        .param("level", "SIDO")
                        .param("hasMembers", "true")
                        .with(authentication(login())))
                .andExpect(status().isOk());

        verify(queryService).findRegionOptions(RegionLevel.SIDO, RegionScope.ALL, true);
    }

    @Test
    @DisplayName("level이 없거나 값이 아니면 400이고 서비스를 부르지 않는다")
    void regionOptionsRequiresValidLevel() throws Exception {
        mockMvc.perform(get("/api/v1/communities/regions").with(authentication(login())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/communities/regions")
                        .param("level", "EUPMYEONDONG").with(authentication(login())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("로그인하지 않은 단계 옵션 요청은 막혀 서비스까지 오지 않는다")
    void regionOptionsRequiresLogin() throws Exception {
        // 이 슬라이스에는 운영 SecurityFilterChain이 없어 Boot 기본 보안(OAuth2 리다이렉트)이 응답한다.
        // ItemControllerTest와 같은 기준으로 "막혔는지"만 본다
        mockMvc.perform(get("/api/v1/communities/regions").param("level", "SIDO"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(300));

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("size는 최대 50이다")
    void sizeIsCappedAtFifty() throws Exception {
        mockMvc.perform(get("/api/v1/communities")
                        .param("size", "51").with(authentication(login())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("동네 정보 조회는 비활성 동네를 404로 전달한다")
    void inactiveCommunityIsNotFound() throws Exception {
        when(queryService.findActive(100L))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/communities/100").with(authentication(login())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("주변 동네 찾기는 좌표를 전달하고 truncated를 내린다")
    void nearbyPassesLocation() throws Exception {
        when(queryService.findNearby(any()))
                .thenReturn(new NearbyCommunitiesResponse(List.of(community()), true));

        mockMvc.perform(post("/api/v1/communities/nearby")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(LOCATION_BODY)
                        .with(authentication(login())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.items[0].name").value("새솔마을 5단지"));

        var captor = ArgumentCaptor.forClass(VerifyLocationRequest.class);
        verify(queryService).findNearby(captor.capture());
        assertThat(captor.getValue().latitude()).isEqualTo(37.5);
        assertThat(captor.getValue().accuracyMeters()).isEqualTo(12.5);
    }

    @Test
    @DisplayName("/nearby는 동네 ID 경로보다 먼저 해석된다")
    void nearbyPathWinsOverCommunityId() throws Exception {
        when(queryService.findNearby(any()))
                .thenReturn(new NearbyCommunitiesResponse(List.of(), false));

        mockMvc.perform(post("/api/v1/communities/nearby")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(LOCATION_BODY)
                        .with(authentication(login())).with(csrf()))
                .andExpect(status().isOk());

        verifyNoInteractions(verificationService);
    }

    @Test
    @DisplayName("위치 인증은 소속을 내려주고 좌표는 응답에 담지 않는다")
    void verifyReturnsMembership() throws Exception {
        when(verificationService.verify(anyLong(), anyLong(), any())).thenReturn(
                new MembershipResponse("55", "100", "새솔마을 5단지", "ACTIVE",
                        Instant.parse("2026-10-13T01:00:00Z"),
                        Instant.parse("2026-11-12T01:00:00Z"), true));

        mockMvc.perform(post("/api/v1/communities/100/verifications")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(LOCATION_BODY)
                        .with(authentication(login())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityId").value("100"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist());

        verify(verificationService).verify(eq(ME), eq(100L), any());
    }

    @Test
    @DisplayName("반경 밖은 403으로 전달한다")
    void outsideRadiusIsForbidden() throws Exception {
        verificationFails(ErrorCode.LOCATION_OUTSIDE);

        verifyRequest()
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCATION_OUTSIDE"));
    }

    @Test
    @DisplayName("정확도 초과는 422로 전달한다")
    void inaccurateLocationIsUnprocessable() throws Exception {
        verificationFails(ErrorCode.LOCATION_INACCURATE);

        verifyRequest()
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LOCATION_INACCURATE"));
    }

    /** 예외를 던지는 스텁은 doThrow로 지정한다. when()은 스텁 중에 메서드를 실제로 호출한다 */
    private void verificationFails(ErrorCode errorCode) {
        doThrow(new BusinessException(errorCode))
                .when(verificationService).verify(anyLong(), anyLong(), any());
    }

    private ResultActions verifyRequest() throws Exception {
        return mockMvc.perform(post("/api/v1/communities/100/verifications")
                .header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(LOCATION_BODY)
                .with(authentication(login())).with(csrf()));
    }

    @Test
    @DisplayName("정확도가 큰 값이어도 400이 아니다. 판정은 서비스가 422로 한다")
    void largeAccuracyIsNotABindingError() throws Exception {
        verificationFails(ErrorCode.LOCATION_INACCURATE);

        mockMvc.perform(post("/api/v1/communities/100/verifications")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5,\"longitude\":127.0,\"accuracyMeters\":5000}")
                        .with(authentication(login())).with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("좌표 범위를 벗어나거나 선언하지 않은 필드가 있으면 400이다")
    void invalidLocationBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/communities/100/verifications")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":91,\"longitude\":127.0,\"accuracyMeters\":10}")
                        .with(authentication(login())).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/communities/100/verifications")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":37.5,\"longitude\":127.0,\"accuracyMeters\":10,\"x\":1}")
                        .with(authentication(login())).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(verificationService);
    }

    @Test
    @DisplayName("Idempotency-Key가 UUID가 아니면 400이다")
    void verifyRequiresUuidIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/communities/100/verifications")
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON).content(LOCATION_BODY)
                        .with(authentication(login())).with(csrf()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(verificationService);
    }

    @Test
    @DisplayName("공용 장소 목록은 guide가 없으면 생략한다")
    void placeListOmitsMissingGuide() throws Exception {
        when(placeService.findPlaces(ME, 100L)).thenReturn(new PlaceListResponse(List.of(
                new PlaceResponse("10", "100", "관리동 무인택배함",
                        new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), null))));

        mockMvc.perform(get("/api/v1/communities/100/places").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("10"))
                .andExpect(jsonPath("$.items[0].communityId").value("100"))
                .andExpect(jsonPath("$.items[0].guide").doesNotExist());
    }
}
