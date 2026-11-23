package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import java.util.concurrent.RejectedExecutionException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatMessageCreateWebSocketListenerTest {

    private final ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    private final TaskExecutor executor = mock(TaskExecutor.class);
    private final ChatMessageCreateWebSocketListener listener =
        new ChatMessageCreateWebSocketListener(handler, executor);

    @Test
    @DisplayName("커밋된 메시지의 로컬 전달을 요청 흐름 밖으로 예약한다")
    void broadcastsCreatedMessage() {
        MessageResponse message = mock(MessageResponse.class);
        listener.onMessageCreated(new ChatMessageCreatedEvent(901L, message));

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(task.capture());
        verifyNoInteractions(handler);

        task.getValue().run();
        verify(handler).broadcastCommittedMessage(901L, message);
    }

    @Test
    @DisplayName("전달 예약이 거부되어도 저장 응답 흐름은 실패하지 않는다")
    void rejectedDeliveryFallsBackToPolling() {
        doThrow(new RejectedExecutionException("full")).when(executor).execute(any(Runnable.class));

        assertDoesNotThrow(() -> listener.onMessageCreated(
            new ChatMessageCreatedEvent(901L, mock(MessageResponse.class))
        ));
        verifyNoInteractions(handler);
    }
}
