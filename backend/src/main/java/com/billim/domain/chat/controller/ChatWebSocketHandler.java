package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import com.billim.domain.chat.service.ChatMessageQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 64 * 1024;
    private static final int REPLAY_PAGE_SIZE = 50;

    private final ChatRoomAccessService accessService;
    private final ChatMessageQueryService messageQueryService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WebSocketSession>> sessionByRoom = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Object roomValue = attributes.get(ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE);
        Object memberValue = attributes.get(ChatWebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE);
        Object authSessionValue = attributes.get(ChatWebSocketHandshakeInterceptor.AUTH_SESSION_ID_ATTRIBUTE);
        Object replayValue = attributes.get(ChatWebSocketHandshakeInterceptor.REPLAY_AFTER_SEQUENCE_ATTRIBUTE);

        if (!(roomValue instanceof Long roomId)
            || !(memberValue instanceof Long memberId)
            || !(authSessionValue instanceof Long authSessionId)
            || !(replayValue instanceof Long replayAfterSequence)
            || !accessService.canReceive(roomId, memberId, authSessionId)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
            session,
            SEND_TIME_LIMIT_MS,
            BUFFER_SIZE_LIMIT_BYTES
        );
        sessionByRoom.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>())
            .put(session.getId(), safeSession);
        try {
            long replayThroughSequence = accessService
                .findLastSequenceForParticipant(roomId, memberId)
                .orElseThrow();
            replayMissedMessages(
                roomId, memberId, authSessionId, replayAfterSequence,
                replayThroughSequence, safeSession
            );
        } catch (Exception exception) {
            log.warn("채팅 WebSocket 연결 복구 실패: roomId={}", roomId, exception);
            closeAndRemove(roomId, session.getId(), safeSession);
        }
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

        if (value instanceof Long roomId) {
            removeSession(roomId, session.getId());
        }
    }


    public void broadcast(Long roomId, String payload) {
        Map<String, WebSocketSession> sessions = sessionByRoom.get(roomId);
        if (sessions == null) {
            return;
        }

        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            WebSocketSession session = entry.getValue();

            Object memberValue = session.getAttributes()
                .get(ChatWebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE);
            Object authSessionValue = session.getAttributes()
                .get(ChatWebSocketHandshakeInterceptor.AUTH_SESSION_ID_ATTRIBUTE);

            if (!(memberValue instanceof Long memberId)
                || !(authSessionValue instanceof Long authSessionId)) {
                closeAndRemove(roomId, sessionId, session);
                continue;
            }

            try {
                if (!session.isOpen()
                    || !accessService.canReceive(roomId, memberId, authSessionId)) {
                    closeAndRemove(roomId, sessionId, session);
                    continue;
                }

                session.sendMessage(new TextMessage(payload));
            } catch (Exception exception) {
                log.warn("채팅 WebSocket 전달 실패: roomId={}, sessionId={}", roomId, sessionId, exception);
                closeAndRemove(roomId, sessionId, session);
            }
        }
    }

    private void replayMissedMessages(
        Long roomId,
        Long memberId,
        Long authSessionId,
        long afterSequence,
        long throughSequence,
        WebSocketSession session
    ) throws Exception {
        while (afterSequence < throughSequence) {
            if (!session.isOpen() || !accessService.canReceive(roomId, memberId, authSessionId)) {
                throw new IllegalStateException("채팅 WebSocket 수신 권한이 종료되었습니다.");
            }

            var page = messageQueryService.getMessages(
                roomId, memberId, afterSequence, null, REPLAY_PAGE_SIZE
            );
            boolean advanced = false;
            for (var message : page.items()) {
                if (message.sequence() > throughSequence) {
                    return;
                }
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                afterSequence = message.sequence();
                advanced = true;
            }
            if (!advanced || !page.hasMore()) {
                return;
            }
        }
    }

    private void closeAndRemove(
        Long roomId,
        String sessionId,
        WebSocketSession session
    ) {
        removeSession(roomId, sessionId);
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.POLICY_VIOLATION);
            }
        } catch (IOException exception) {
            log.debug("채팅 WebSocket 연결 종료 실패: sessionId={}", sessionId, exception);
        }
    }

    private void removeSession(Long roomId, String sessionId) {
        sessionByRoom.computeIfPresent(roomId, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

}
