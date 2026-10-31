package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatRoomAccessService;
import com.billim.domain.chat.service.ChatMessageQueryService;
import com.billim.domain.chat.dto.MessagePageResponse;
import com.billim.domain.chat.dto.MessageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.List;
import java.time.Instant;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class ChatWebSocketHandlerTest {

    private final ChatRoomAccessService accessService = mock(ChatRoomAccessService.class);
    private final ChatMessageQueryService messageQueryService = mock(ChatMessageQueryService.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final ChatWebSocketHandler handler = new ChatWebSocketHandler(
        accessService, messageQueryService, objectMapper
    );

    @Test
    @DisplayName("유휴 연결 유지 신호는 채팅 메시지나 DB 재조회를 만들지 않는다")
    void heartbeatDoesNotQueryOrAdvanceMessages() throws Exception {
        WebSocketSession session = session("session-heartbeat", 2L, 55L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true);
        when(accessService.findLastSequenceForParticipant(901L, 2L)).thenReturn(Optional.of(0L));
        handler.afterConnectionEstablished(session);
        org.mockito.Mockito.clearInvocations(accessService);

        handler.sendHeartbeats();

        verify(session).sendMessage(argThat(message -> message instanceof org.springframework.web.socket.PingMessage));
        verifyNoInteractions(accessService, messageQueryService);
    }

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
        MessageResponse message = message(1L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true);
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.of(0L), Optional.of(1L));
        when(messageQueryService.getMessages(901L, 2L, 0L, null, 50))
            .thenReturn(new MessagePageResponse(List.of(message), false, null, null, 0));
        when(objectMapper.writeValueAsString(message)).thenReturn("{\"id\":\"701\"}");

        handler.afterConnectionEstablished(session);
        handler.refreshRoom(901L);
        handler.refreshRoom(901L);

        verify(session, times(1)).sendMessage(argThat(sentMessage ->
            sentMessage instanceof TextMessage textMessage
                && textMessage.getPayload().equals("{\"id\":\"701\"}")));
    }

    @Test
    @DisplayName("같은 방의 두 연결은 재확인 때 방 순번을 한 번만 조회한다")
    void refreshRoomReadsSequenceOnceForTwoSessions() throws Exception {
        WebSocketSession owner = session("session-owner", 2L, 55L);
        WebSocketSession requester = session("session-requester", 3L, 56L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true);
        when(accessService.canReceive(901L, 3L, 56L)).thenReturn(true);
        when(accessService.findLastSequenceForParticipant(901L, 2L)).thenReturn(Optional.of(0L));
        when(accessService.findLastSequenceForParticipant(901L, 3L)).thenReturn(Optional.of(0L));

        handler.afterConnectionEstablished(owner);
        handler.afterConnectionEstablished(requester);
        org.mockito.Mockito.clearInvocations(accessService);

        handler.refreshRoom(901L);

        verify(accessService, times(1)).findLastSequenceForParticipant(
            org.mockito.ArgumentMatchers.eq(901L), org.mockito.ArgumentMatchers.anyLong());
        verify(accessService).canReceive(901L, 2L, 55L);
        verify(accessService).canReceive(901L, 3L, 56L);
        verifyNoInteractions(messageQueryService);
    }

    @Test
    @DisplayName("로컬 커밋 메시지는 DB 재조회 없이 한 번만 전달한다")
    void committedMessageUsesDirectDeliveryOnce() throws Exception {
        WebSocketSession session = session("session-1", 2L, 55L);
        MessageResponse message = message(1L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true);
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.of(0L));
        when(objectMapper.writeValueAsString(message)).thenReturn("{\"id\":\"701\"}");

        handler.afterConnectionEstablished(session);
        handler.broadcastCommittedMessage(901L, message);
        handler.broadcastCommittedMessage(901L, message);

        verify(session, times(1)).sendMessage(argThat(sent ->
            sent instanceof TextMessage text
                && text.getPayload().equals("{\"id\":\"701\"}")));
        verifyNoInteractions(messageQueryService);
    }

    @Test
    @DisplayName("로컬 전달 순서가 앞서면 DB에서 빠진 순번부터 복구한다")
    void committedMessageReplaysSequenceGap() throws Exception {
        WebSocketSession session = session("session-1", 2L, 55L);
        MessageResponse first = message(1L);
        MessageResponse second = message(2L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true);
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.of(0L), Optional.of(2L));
        when(messageQueryService.getMessages(901L, 2L, 0L, null, 50))
            .thenReturn(new MessagePageResponse(List.of(first, second), false, null, null, 0));
        when(objectMapper.writeValueAsString(first)).thenReturn("first");
        when(objectMapper.writeValueAsString(second)).thenReturn("second");

        handler.afterConnectionEstablished(session);
        handler.broadcastCommittedMessage(901L, second);

        InOrder order = inOrder(session);
        order.verify(session).sendMessage(argThat(sent ->
            sent instanceof TextMessage text && text.getPayload().equals("first")));
        order.verify(session).sendMessage(argThat(sent ->
            sent instanceof TextMessage text && text.getPayload().equals("second")));
    }

    @Test
    @DisplayName("전달 시 권한이 사라진 연결은 종료하고 메시지를 보내지 않는다")
    void revokedConnectionDoesNotReceiveMessage() throws Exception {
        WebSocketSession session = session("session-1", 2L, 55L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true, true, false);
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.of(0L));

        handler.afterConnectionEstablished(session);
        handler.refreshRoom(901L);

        verify(session, never()).sendMessage(any());
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("핸드셰이크 뒤 등록 전 저장된 메시지를 연결 직후 재전달한다")
    void messageCommittedDuringConnectionIsReplayed() throws Exception {
        WebSocketSession session = session("session-1", 2L, 55L);
        MessageResponse message = message(1L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true);
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.of(1L));
        when(messageQueryService.getMessages(901L, 2L, 0L, null, 50))
            .thenReturn(new MessagePageResponse(List.of(message), false, null, null, 0));
        when(objectMapper.writeValueAsString(message)).thenReturn("{\"id\":\"701\"}");

        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(argThat(sent ->
            sent instanceof TextMessage text
                && text.getPayload().equals("{\"id\":\"701\"}")));
    }

    @Test
    @DisplayName("재전달 도중 권한이 사라지면 연결을 종료한다")
    void replayStopsAfterRevocation() throws Exception {
        WebSocketSession session = session("session-1", 2L, 55L);
        when(accessService.canReceive(901L, 2L, 55L)).thenReturn(true, false);
        when(accessService.findLastSequenceForParticipant(901L, 2L))
            .thenReturn(Optional.of(1L));

        handler.afterConnectionEstablished(session);

        verify(session, never()).sendMessage(any());
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    private MessageResponse message(long sequence) {
        return new MessageResponse(
            "701", "901", sequence, "TEXT", "2", "hello", null, null,
            null, null, Instant.parse("2026-10-19T00:00:00Z")
        );
    }

    private WebSocketSession session(String sessionId, Long memberId, Long authSessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(Map.of(
            ChatWebSocketHandshakeInterceptor.ROOM_ID_ATTRIBUTE, 901L,
            ChatWebSocketHandshakeInterceptor.MEMBER_ID_ATTRIBUTE, memberId,
            ChatWebSocketHandshakeInterceptor.AUTH_SESSION_ID_ATTRIBUTE, authSessionId,
            ChatWebSocketHandshakeInterceptor.REPLAY_AFTER_SEQUENCE_ATTRIBUTE, 0L
        ));
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
