package com.billim.domain.chat.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    private final ChatWebSocketHandler handler = new ChatWebSocketHandler();

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
}
