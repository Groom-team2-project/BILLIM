package com.billim.domain.community.controller;

import com.billim.domain.community.dto.MyCommunitiesResponse;
import com.billim.domain.community.dto.MyCommunitiesResponse.MyCommunity;
import com.billim.domain.community.service.MyCommunityService;
import com.billim.domain.member.entity.MemberRole;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 내 동네 API 계약 (API 명세 E_074 ~ E_076). 운영 Security 설정은 건드리지 않는다 */
@WebMvcTest(controllers = MyCommunityController.class, properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
class MyCommunityControllerTest {

    private static final long ME = 7L;
    private static final String KEY = "4429a68a-0270-4c19-9c5f-427b08aaac87";

    @Autowired MockMvc mockMvc;
    @MockitoBean MyCommunityService myCommunityService;

    private static Authentication login() {
        LoginMember member = new LoginMember(ME, MemberRole.USER);
        return new OAuth2AuthenticationToken(member, member.getAuthorities(), "kakao");
    }

    private static MyCommunitiesResponse mine() {
        Instant now = Instant.parse("2026-11-10T00:00:00Z");
        return new MyCommunitiesResponse(List.of(
                new MyCommunity("100", "역삼1동", "서울특별시 강남구 역삼1동",
                        false, true, now, now.plusSeconds(86400)),
                new MyCommunity("200", "망원동", "서울특별시 마포구 망원동",
                        true, true, now, now.plusSeconds(86400))),
                "200");
    }

    @Test
    @DisplayName("내 동네 목록은 저장 동네와 현재 동네를 ID 문자열로 내린다")
    void findMine() throws Exception {
        when(myCommunityService.findMine(ME)).thenReturn(mine());

        mockMvc.perform(get("/api/v1/members/me/communities").with(authentication(login())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentCommunityId").value("200"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].communityId").value("100"))
                .andExpect(jsonPath("$.items[0].isCurrent").value(false))
                .andExpect(jsonPath("$.items[1].isCurrent").value(true))
                .andExpect(jsonPath("$.items[1].displayAddress").value("서울특별시 마포구 망원동"));
    }

    @Test
    @DisplayName("현재 동네 변경은 회원과 동네를 서비스에 전달한다")
    void select() throws Exception {
        when(myCommunityService.select(ME, 100L)).thenReturn(mine());

        mockMvc.perform(post("/api/v1/members/me/communities/100/select")
                        .with(csrf()).header("Idempotency-Key", KEY)
                        .with(authentication(login())))
                .andExpect(status().isOk());

        verify(myCommunityService).select(ME, 100L);
    }

    @Test
    @DisplayName("현재 동네 변경에 Idempotency-Key가 없으면 400이다")
    void selectRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/members/me/communities/100/select")
                        .with(csrf()).with(authentication(login())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(myCommunityService);
    }

    @Test
    @DisplayName("동네 ID는 양수여야 한다")
    void communityIdMustBePositive() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me/communities/0")
                        .with(csrf()).with(authentication(login())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(myCommunityService);
    }

    @Test
    @DisplayName("저장 동네 이탈은 회원과 동네를 서비스에 전달한다")
    void leave() throws Exception {
        when(myCommunityService.leave(ME, 100L)).thenReturn(mine());

        mockMvc.perform(delete("/api/v1/members/me/communities/100")
                        .with(csrf()).with(authentication(login())))
                .andExpect(status().isOk());

        verify(myCommunityService).leave(ME, 100L);
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 막혀 서비스까지 오지 않는다")
    void requiresLogin() throws Exception {
        // 이 슬라이스에는 운영 SecurityFilterChain이 없어 Boot 기본 보안이 응답한다.
        // ItemControllerTest와 같은 기준으로 "막혔는지"만 본다
        mockMvc.perform(get("/api/v1/members/me/communities"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(300));

        verifyNoInteractions(myCommunityService);
    }

    @Test
    @DisplayName("회원 ID를 읽을 수 없는 인증 정보는 401이다")
    void unreadableMemberIsRejected() throws Exception {
        LoginMember broken = new LoginMember(null, MemberRole.USER);
        Authentication auth =
                new OAuth2AuthenticationToken(broken, broken.getAuthorities(), "kakao");

        mockMvc.perform(get("/api/v1/members/me/communities").with(authentication(auth)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(myCommunityService, org.mockito.Mockito.never()).findMine(anyLong());
    }
}
