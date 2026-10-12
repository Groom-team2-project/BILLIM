package com.billim.domain.notification.controller;

import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.notification.dto.NotificationPageResponse;
import com.billim.domain.notification.dto.NotificationResponse;
import com.billim.domain.notification.dto.UnreadCountResponse;
import com.billim.domain.notification.service.NotificationQueryService;
import com.billim.domain.notification.service.NotificationReadService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 API 계약 (API 명세 11장). 운영 Security 설정은 건드리지 않는다.
 * 인증 주체는 실제와 같은 {@link LoginMember}로 만든다 (members.id를 담는 계약).
 */
@WebMvcTest(controllers = NotificationController.class, properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
})
class NotificationControllerTest {

    private static final long ME = 7L;

    @Autowired MockMvc mockMvc;
    @MockitoBean NotificationQueryService queryService;
    @MockitoBean NotificationReadService readService;

    /** 로그인 회원. principal이 LoginMember인 점까지 운영과 같게 만든다 */
    private static Authentication loginMember() {
        LoginMember member = new LoginMember(ME, MemberRole.USER);
        return new OAuth2AuthenticationToken(member, member.getAuthorities(), "kakao");
    }

    private static NotificationResponse notification(long id, Instant readAt) {
        return new NotificationResponse(String.valueOf(id), "RENTAL_APPROVED",
                "정우 님이 전동드릴 대여 요청을 승인했어요", "RENTAL", "501",
                readAt, Instant.parse("2026-10-10T01:00:00Z"));
    }

    @Test
    @DisplayName("목록은 ID를 문자열로 내리고, 미열람 알림의 readAt은 생략한다")
    void listReturnsStringIdsAndOmitsNullReadAt() throws Exception {
        when(queryService.list(ME, null, 20, null)).thenReturn(
                new NotificationPageResponse(List.of(notification(801, null)), true, "801"));

        mockMvc.perform(get("/api/v1/notifications").with(authentication(loginMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("801"))
                .andExpect(jsonPath("$.items[0].targetId").value("501"))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value("801"));
    }

    @Test
    @DisplayName("더 받을 것이 없으면 nextCursor를 생략한다")
    void lastPageOmitsNextCursor() throws Exception {
        when(queryService.list(ME, null, 20, null)).thenReturn(
                new NotificationPageResponse(List.of(), false, null));

        mockMvc.perform(get("/api/v1/notifications").with(authentication(loginMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("cursor·limit·read 질의를 그대로 서비스에 전달한다")
    void passesQueryParameters() throws Exception {
        when(queryService.list(ME, 801L, 10, false)).thenReturn(
                new NotificationPageResponse(List.of(), false, null));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("cursor", "801")
                        .param("limit", "10")
                        .param("read", "false")
                        .with(authentication(loginMember())))
                .andExpect(status().isOk());

        verify(queryService).list(ME, 801L, 10, false);
    }

    @Test
    @DisplayName("limit은 최대 50이다")
    void limitIsCappedAtFifty() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("limit", "51")
                        .with(authentication(loginMember())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("cursor가 ID 형식이 아니면 400이다")
    void malformedCursorIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .param("cursor", "0")
                        .with(authentication(loginMember())))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    @DisplayName("안 읽은 수를 내려준다")
    void returnsUnreadCount() throws Exception {
        when(queryService.unreadCount(ME)).thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(authentication(loginMember())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    @DisplayName("개별 읽음은 읽은 알림을 돌려준다")
    void markReadReturnsNotification() throws Exception {
        Instant readAt = Instant.parse("2026-10-10T02:00:00Z");
        when(readService.markRead(ME, 801L)).thenReturn(notification(801, readAt));

        mockMvc.perform(put("/api/v1/notifications/801/read")
                        .with(authentication(loginMember())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("801"))
                .andExpect(jsonPath("$.readAt").value("2026-10-10T02:00:00Z"));
    }

    @Test
    @DisplayName("타인의 알림 읽음 시도는 404로 전달한다")
    void markReadOnOtherMemberIsNotFound() throws Exception {
        when(readService.markRead(ME, 801L))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(put("/api/v1/notifications/801/read")
                        .with(authentication(loginMember())).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("모두 읽음은 204이고 본문이 없다")
    void markAllReadReturnsNoContent() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"throughId\":\"801\"}")
                        .with(authentication(loginMember())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(readService).markAllRead(ME, 801L);
    }

    @Test
    @DisplayName("throughId가 없거나 형식이 다르면 400이고 읽음 처리하지 않는다")
    void invalidThroughIdIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(authentication(loginMember())).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"throughId\":\"abc\"}")
                        .with(authentication(loginMember())).with(csrf()))
                .andExpect(status().isBadRequest());

        verify(readService, never()).markAllRead(anyLong(), anyLong());
    }

    @Test
    @DisplayName("선언하지 않은 요청 필드는 400이다 (명세 0.1)")
    void unknownRequestFieldIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"throughId\":\"801\",\"unknown\":1}")
                        .with(authentication(loginMember())).with(csrf()))
                .andExpect(status().isBadRequest());

        verify(readService, never()).markAllRead(anyLong(), anyLong());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("미인증 요청은 Security에서 막혀 서비스까지 오지 않는다")
    void anonymousRequestIsRejected() throws Exception {
        // 이 슬라이스에는 운영 SecurityFilterChain이 없어 Spring Boot 기본 보안이 응답한다.
        // 명세의 401 UNAUTHENTICATED JSON 응답은 AuthSecurityTest가 운영 설정으로 확인한다.
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(300));

        verifyNoInteractions(queryService);
    }
}
