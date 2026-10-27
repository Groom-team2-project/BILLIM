package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.ChatSocketTicketResponse;
import com.billim.domain.chat.service.ChatSocketTicketService;
import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.global.security.LoginMember;
import com.billim.global.security.session.CurrentAuthSession;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ChatSocketTicketController.class, properties = {
    "spring.security.oauth2.client.registration.kakao.client-id=test",
    "spring.security.oauth2.client.registration.kakao.client-secret=test"
})
class ChatSocketTicketControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ChatSocketTicketService tickets;
    @MockitoBean CurrentAuthSession sessions;

    @Test
    @DisplayName("인증·CSRF 검증 후 현재 세션으로 연결권 발급 및 캐시 금지")
    void issueUsesCurrentSessionAndPreventsCaching() throws Exception {
        LoginMember principal = new LoginMember(2L, MemberRole.USER);
        Member member = mock(Member.class);
        AuthSession session = mock(AuthSession.class);
        when(member.getId()).thenReturn(2L);
        when(session.getMember()).thenReturn(member);
        when(session.getId()).thenReturn(3L);
        when(session.isLoggedIn()).thenReturn(true);
        when(sessions.get(any())).thenReturn(Optional.of(session));
        when(tickets.issue(1L, 2L, 3L)).thenReturn(new ChatSocketTicketResponse("ticket", Instant.parse("2026-10-27T00:00:30Z")));

        mvc.perform(post("/api/v1/chat/rooms/1/socket-tickets")
                .with(authentication(new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao")))
                .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.ticket").value("ticket"))
            .andExpect(jsonPath("$.expiresAt").value("2026-10-27T00:00:30Z"));
        verify(tickets).issue(1L, 2L, 3L);
    }

    @Test
    @DisplayName("CSRF 없는 연결권 발급 차단")
    void missingCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/v1/chat/rooms/1/socket-tickets").with(user("user")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(tickets);
    }
}
