package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import com.billim.domain.member.entity.MemberRole;
import com.billim.global.security.LoginMember;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketHandshakeInterceptorTest {

    private final ChatRoomAccessService accessService = mock(ChatRoomAccessService.class);
    private final ChatWebSocketHandshakeInterceptor interceptor =
        new ChatWebSocketHandshakeInterceptor(accessService);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @Test
    @DisplayName("채팅방 참여자는 연결할 수 있고 검증된 ID가 세션에 전달된다")
    void participantCanConnect() {
        HttpServletRequest servletRequest = authenticatedRequest("901");
        when(accessService.isParticipant(901L, 2L)).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest), response, handler, attributes);

        assertTrue(allowed);
        assertEquals(901L, attributes.get(ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE));
        assertEquals(2L, attributes.get(ChatWebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE));
        verify(accessService).isParticipant(901L, 2L);
    }

    @Test
    @DisplayName("인증되지 않은 연결은 채팅방을 조회하지 않고 거부한다")
    void unauthenticatedRequestIsRejected() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        boolean allowed = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest), response, handler, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(accessService, never()).isParticipant(901L, 2L);
    }

    @Test
    @DisplayName("roomId를 중복 전달하면 연결을 거부한다")
    void duplicateRoomIdsAreRejected() {
        HttpServletRequest servletRequest = authenticatedRequest("901", "902");

        boolean allowed = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest), response, handler, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        verify(accessService, never()).isParticipant(901L, 2L);
    }

    @Test
    @DisplayName("채팅방 참여자가 아니면 연결을 거부한다")
    void nonParticipantIsRejected() {
        HttpServletRequest servletRequest = authenticatedRequest("901");
        when(accessService.isParticipant(901L, 2L)).thenReturn(false);

        boolean allowed = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest), response, handler, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.NOT_FOUND);
    }

    private HttpServletRequest authenticatedRequest(String... roomIds) {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        LoginMember member = new LoginMember(2L, MemberRole.USER);
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                member, null, member.getAuthorities());
        when(servletRequest.getUserPrincipal()).thenReturn(authentication);
        when(servletRequest.getParameterValues("roomId")).thenReturn(roomIds);
        return servletRequest;
    }
}
