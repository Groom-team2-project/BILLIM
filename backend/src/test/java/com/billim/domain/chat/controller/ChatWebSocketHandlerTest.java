package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    private final ChatRoomAccessService accessService = mock(ChatRoomAccessService.class);
    private final ChatWebSocketHandler handler = new ChatWebSocketHandler(accessService);

    @Test
    @DisplayName("검증된 채팅방 ID가 없는 연결은 종료한다")
    void connectionWithoutRoomIsClosed() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of());

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("WebSocket 직접 메시지 전송은 연결을 종료한다")
    void clientMessageIsRejected() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);

        handler.handleMessage(session, new TextMessage("hello"));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("유효한 연결은 메시지를 수신한다")
    void authorizedConnectionReceivesMessage() throws Exception {
        WebSocketSession session = session("session-1", 2L, 55L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true);

        handler.afterConnectionEstablished(session);
        handler.broadcast(901L, "{\"id\":\"701\"}");

        verify(session).sendMessage(argThat(message ->
            message instanceof TextMessage textMessage
                && textMessage.getPayload().equals("{\"id\":\"701\"}")));
    }

    @Test
    @DisplayName("전달 시 권한이 사라진 연결은 종료하고 메시지를 보내지 않는다")
    void revokedConnectionDoesNotReceiveMessage() throws Exception {
        WebSocketSession session = session("session-1", 2L, 55L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true, false);

        handler.afterConnectionEstablished(session);
        handler.broadcast(901L, "{\"id\":\"701\"}");

        verify(session, never()).sendMessage(any());
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    private WebSocketSession session(String sessionId, Long memberId, Long authSessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of(
            ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE, 901L,
            ChatWebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE, memberId,
            ChatWebSocketHandshakeInterceptor.AUTH_SESSION_ID_ATTRIBUTE, authSessionId
        ));
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
