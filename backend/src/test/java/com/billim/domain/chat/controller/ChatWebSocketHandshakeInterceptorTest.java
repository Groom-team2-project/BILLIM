package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import com.billim.domain.chat.service.ChatSocketTicketService;
import com.billim.domain.member.entity.AuthSession;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.global.security.LoginMember;
import com.billim.global.security.session.CurrentAuthSession;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketHandshakeInterceptorTest {

    private final ChatRoomAccessService accessService = mock(ChatRoomAccessService.class);
    private final CurrentAuthSession currentAuthSession = mock(CurrentAuthSession.class);
    private final ChatSocketTicketService ticketService = mock(ChatSocketTicketService.class);
    private final ChatWebSocketHandshakeInterceptor interceptor =
        new ChatWebSocketHandshakeInterceptor(accessService, currentAuthSession, ticketService);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @Test
    @DisplayName("채팅방 참여자는 연결할 수 있고 검증된 ID가 세션에 전달된다")
    void participantCanConnect() {
        HttpServletRequest servletRequest = authenticatedRequest("901");
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.of(12L));
        Map<String, Object> attributes = new HashMap<>();

        boolean allowed = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest), response, handler, attributes);

        assertTrue(allowed);
        assertEquals(901L, attributes.get(ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE));
        assertEquals(2L, attributes.get(ChatWebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE));
        assertEquals(55L, attributes.get(ChatWebSocketHandshakeInterceptor.AUTH_SESSION_ID_ATTRIBUTE));
        assertEquals(12L, attributes.get(ChatWebSocketHandshakeInterceptor.REPLAY_AFTER_SEQUENCE_ATTRIBUTE));
        verify(accessService).findLastSequenceForParticipant(901L, 2L);
    }

    @Test
    @DisplayName("인증되지 않은 연결은 채팅방을 조회하지 않고 거부한다")
    void unauthenticatedRequestIsRejected() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        boolean allowed = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest), response, handler, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(accessService, never()).findLastSequenceForParticipant(901L, 2L);
    }

    @Test
    @DisplayName("roomId를 중복 전달하면 연결을 거부한다")
    void duplicateRoomIdsAreRejected() {
        HttpServletRequest servletRequest = authenticatedRequest("901", "902");

        boolean allowed = interceptor.beforeHandshake(
            new ServletServerHttpRequest(servletRequest), response, handler, new HashMap<>());

        assertFalse(allowed);
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
        verify(accessService, never()).findLastSequenceForParticipant(901L, 2L);
    }

    @Test
    @DisplayName("채팅방 참여자가 아니면 연결을 거부한다")
    void nonParticipantIsRejected() {
        HttpServletRequest servletRequest = authenticatedRequest("901");
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.empty());

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
        AuthSession authSession = mock(AuthSession.class);
        Member sessionMember = mock(Member.class);
        when(authSession.isLoggedIn()).thenReturn(true);
        when(authSession.getMember()).thenReturn(sessionMember);
        when(sessionMember.getId()).thenReturn(2L);
        when(authSession.getId()).thenReturn(55L);
        when(currentAuthSession.get(servletRequest)).thenReturn(Optional.of(authSession));
        return servletRequest;
    }

    @Test
    @DisplayName("허용 출처의 연결권으로 쿠키 없는 연결을 인증한다")
    void ticketAuthenticatesWithoutCookie() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterValues("ticket")).thenReturn(new String[]{"ticket"});
        when(request.getParameterValues("roomId")).thenReturn(new String[]{"901"});
        when(request.getHeader("Origin")).thenReturn("https://billim.netlify.app");
        when(ticketService.acceptsOrigin("https://billim.netlify.app")).thenReturn(true);
        when(ticketService.consume("ticket", 901L)).thenReturn(new ChatSocketTicketService.Identity(901L, 2L, 55L));
        when(accessService.findLastSequenceForParticipant(901L, 2L)).thenReturn(Optional.of(12L));
        Map<String, Object> attributes = new HashMap<>();

        assertTrue(interceptor.beforeHandshake(new ServletServerHttpRequest(request), response, handler, attributes));
        assertEquals(55L, attributes.get(ChatWebSocketHandshakeInterceptor.AUTH_SESSION_ID_ATTRIBUTE));
        assertEquals(2L, attributes.get(ChatWebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE));
    }

    @Test
    @DisplayName("허용하지 않은 출처는 연결권을 소비하지 않고 거부한다")
    void untrustedOriginIsRejectedBeforeConsumption() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterValues("ticket")).thenReturn(new String[]{"ticket"});
        when(request.getHeader("Origin")).thenReturn("https://attacker.example");

        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(request), response, handler, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        verify(ticketService, never()).consume(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("잘못된 연결권은 유효 쿠키가 있어도 쿠키 인증으로 우회하지 않는다")
    void invalidTicketDoesNotFallBackToCookie() {
        HttpServletRequest request = authenticatedRequest("901");
        when(request.getParameterValues("ticket")).thenReturn(new String[]{"invalid"});
        when(request.getHeader("Origin")).thenReturn("https://billim.netlify.app");
        when(ticketService.acceptsOrigin("https://billim.netlify.app")).thenReturn(true);
        when(ticketService.consume("invalid", 901L)).thenThrow(
            new com.billim.global.exception.BusinessException(com.billim.global.exception.ErrorCode.UNAUTHENTICATED));

        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(request), response, handler, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
