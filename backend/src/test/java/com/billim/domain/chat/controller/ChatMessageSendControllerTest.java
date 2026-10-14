package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.SendMessageRequest;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.service.ChatMessageSendService;
import com.billim.domain.member.entity.MemberRole;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = ChatMessageSendController.class,
    properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret"
    }
)
class ChatMessageSendControllerTest {

    private static final UUID CLIENT_MESSAGE_ID =
        UUID.fromString("6d68e9e7-0337-4c5d-bd9b-9d267f23ba16");

    @Autowired MockMvc mvc;
    @MockitoBean ChatMessageSendService sendService;

    @Test
    @DisplayName("인증 회원이 메시지를 보내면 201과 저장된 메시지를 반환한다")
    void sendReturns201WithMessage() throws Exception {
        ChatMessage saved = savedMessage();
        when(sendService.send(eq(901L), eq(2L), any(SendMessageRequest.class)))
            .thenReturn(saved);

        mvc.perform(post("/api/v1/chat/rooms/901/messages")
                .with(loginMember(2L))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientMessageId":"6d68e9e7-0337-4c5d-bd9b-9d267f23ba16",
                      "body":"안녕하세요"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("701"))
            .andExpect(jsonPath("$.roomId").value("901"))
            .andExpect(jsonPath("$.sequence").value(1))
            .andExpect(jsonPath("$.type").value("TEXT"))
            .andExpect(jsonPath("$.senderId").value("2"))
            .andExpect(jsonPath("$.body").value("안녕하세요"))
            .andExpect(jsonPath("$.clientMessageId").value(CLIENT_MESSAGE_ID.toString()));

        verify(sendService).send(eq(901L), eq(2L), any(SendMessageRequest.class));
    }

    @Test
    @DisplayName("빈 메시지는 400으로 거부하고 저장 서비스를 호출하지 않는다")
    void blankBodyReturns400() throws Exception {
        mvc.perform(post("/api/v1/chat/rooms/901/messages")
                .with(loginMember(2L))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientMessageId":"6d68e9e7-0337-4c5d-bd9b-9d267f23ba16",
                      "body":"   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verify(sendService, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("CSRF 토큰이 없는 메시지 전송은 403으로 거부한다")
    void missingCsrfReturns403() throws Exception {
        mvc.perform(post("/api/v1/chat/rooms/901/messages")
                .with(loginMember(2L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "clientMessageId":"6d68e9e7-0337-4c5d-bd9b-9d267f23ba16",
                      "body":"안녕하세요"
                    }
                    """))
            .andExpect(status().isForbidden());

        verify(sendService, never()).send(any(), any(), any());
    }

    private RequestPostProcessor loginMember(Long memberId) {
        LoginMember principal = new LoginMember(memberId, MemberRole.USER);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
            principal,
            principal.getAuthorities(),
            "kakao"
        );
        return authentication(authentication);
    }

    private ChatMessage savedMessage() {
        ChatMessage message = ChatMessage.text(
            901L,
            1L,
            2L,
            "안녕하세요",
            null,
            CLIENT_MESSAGE_ID,
            Instant.parse("2026-10-08T12:00:00Z")
        );
        ReflectionTestUtils.setField(message, "id", 701L);
        return message;
    }
}
