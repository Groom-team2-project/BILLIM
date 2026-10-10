package com.billim.domain.chat.controller;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object value = session.getAttributes()
            .get(ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE);

        if (!(value instanceof Long roomId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        sessionsByRoom.compute(roomId, (id, sessions) -> {
            Set<WebSocketSession> result = sessions == null
                ? ConcurrentHashMap.newKeySet()
                : sessions;
            result.add(session);
            return result;
        });
    }

    @Override
    protected void handleTextMessage(
        WebSocketSession session,
        TextMessage message
    ) throws Exception {
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    @Override
    public void afterConnectionClosed(
        WebSocketSession session,
        CloseStatus status
    ) {
        Object value = session.getAttributes()
            .get(ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE);

        if (!(value instanceof Long roomId)) {
            return;
        }

        sessionsByRoom.computeIfPresent(roomId, (id, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }
}
