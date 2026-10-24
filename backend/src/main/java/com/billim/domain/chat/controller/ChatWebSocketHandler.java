package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatMessageQueryService;
import com.billim.domain.chat.service.ChatRoomAccessService;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
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
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SessionState>> sessionByRoom =
        new ConcurrentHashMap<>();

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
            session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES
        );
        SessionState state = new SessionState(safeSession, memberId, authSessionId, replayAfterSequence);
        sessionByRoom.computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>())
            .put(session.getId(), state);
        refreshRoom(roomId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object value = session.getAttributes()
            .get(ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE);
        if (value instanceof Long roomId) {
            removeSession(roomId, session.getId());
        }
    }

    public Set<Long> activeRoomIds() {
        return Set.copyOf(sessionByRoom.keySet());
    }

    public void refreshRoom(Long roomId) {
        Map<String, SessionState> sessions = sessionByRoom.get(roomId);
        if (sessions == null) {
            return;
        }
        for (Map.Entry<String, SessionState> entry : sessions.entrySet()) {
            SessionState state = entry.getValue();
            synchronized (state) {
                try {
                    replayMissedMessages(roomId, state);
                } catch (Exception exception) {
                    log.warn("채팅 WebSocket 전달 복구 실패: roomId={}, sessionId={}",
                        roomId, entry.getKey(), exception);
                    closeAndRemove(roomId, entry.getKey(), state.session);
                }
            }
        }
    }

    private void replayMissedMessages(Long roomId, SessionState state) throws Exception {
        if (!state.session.isOpen()
            || !accessService.canReceive(roomId, state.memberId, state.authSessionId)) {
            throw new IllegalStateException("채팅 WebSocket 수신 권한이 종료되었습니다.");
        }
        long throughSequence = accessService
            .findLastSequenceForParticipant(roomId, state.memberId).orElseThrow();
        while (state.lastSequence < throughSequence) {
            if (!state.session.isOpen()
                || !accessService.canReceive(roomId, state.memberId, state.authSessionId)) {
                throw new IllegalStateException("채팅 WebSocket 수신 권한이 종료되었습니다.");
            }
            var page = messageQueryService.getMessages(
                roomId, state.memberId, state.lastSequence, null, REPLAY_PAGE_SIZE
            );
            boolean advanced = false;
            for (var message : page.items()) {
                if (message.sequence() > throughSequence) {
                    return;
                }
                state.session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                state.lastSequence = message.sequence();
                advanced = true;
            }
            if (!advanced || !page.hasMore()) {
                return;
            }
        }
    }

    private void closeAndRemove(Long roomId, String sessionId, WebSocketSession session) {
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

    private static final class SessionState {
        private final WebSocketSession session;
        private final Long memberId;
        private final Long authSessionId;
        private long lastSequence;

        private SessionState(WebSocketSession session, Long memberId,
                             Long authSessionId, long lastSequence) {
            this.session = session;
            this.memberId = memberId;
            this.authSessionId = authSessionId;
            this.lastSequence = lastSequence;
        }
    }
}
