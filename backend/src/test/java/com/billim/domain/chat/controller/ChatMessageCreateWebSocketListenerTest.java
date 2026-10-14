package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageCreateWebSocketListenerTest {

    private final ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    private final ObjectMapper objectMapper = mock(ObjectMapper.class);
    private final ChatMessageCreateWebSocketListener listener =
        new ChatMessageCreateWebSocketListener(handler, objectMapper);

    @Test
    @DisplayName("저장된 메시지 응답을 직렬화하여 채팅방에 전달한다")
    void broadcastsCreatedMessage() {
        MessageResponse message = mock(MessageResponse.class);
        when(objectMapper.writeValueAsString(message)).thenReturn("{\"id\":\"701\"}");

        listener.onMessageCreated(new ChatMessageCreatedEvent(901L, message));

        verify(handler).broadcast(901L, "{\"id\":\"701\"}");
    }

    @Test
    @DisplayName("직렬화 실패 시 실시간 전달을 건너뛴다")
    void serializationFailureDoesNotBroadcast() {
        MessageResponse message = mock(MessageResponse.class);
        when(objectMapper.writeValueAsString(message)).thenThrow(new IllegalStateException("invalid"));

        listener.onMessageCreated(new ChatMessageCreatedEvent(901L, message));

        verifyNoInteractions(handler);
    }
}
