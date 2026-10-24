package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatMessageCreateWebSocketListenerTest {

    private final ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    private final ChatMessageCreateWebSocketListener listener =
        new ChatMessageCreateWebSocketListener(handler);

    @Test
    @DisplayName("커밋된 메시지가 있는 채팅방의 연결을 갱신한다")
    void broadcastsCreatedMessage() {
        MessageResponse message = mock(MessageResponse.class);
        listener.onMessageCreated(new ChatMessageCreatedEvent(901L, message));

        verify(handler).refreshRoom(901L);
    }
}
