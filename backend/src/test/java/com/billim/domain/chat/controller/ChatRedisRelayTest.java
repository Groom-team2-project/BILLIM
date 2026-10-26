package com.billim.domain.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

class ChatRedisRelayTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    private final ChatRedisRelay relay = new ChatRedisRelay(
        redisTemplate, handler, new SyncTaskExecutor()
    );

    @Test
    @DisplayName("커밋된 메시지는 내용 없이 서버 식별자와 채팅방 ID만 발행한다")
    void publishesRoomSignal() {
        relay.publishCommittedMessage(new ChatMessageCreatedEvent(901L, mock(MessageResponse.class)));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(ChatRedisRelay.CHANNEL), payload.capture());
        assertThat(payload.getValue()).matches("[^:]+:901:[0-9]+");

        byte[] channel = ChatRedisRelay.CHANNEL.getBytes(StandardCharsets.UTF_8);
        relay.onMessage(new DefaultMessage(
            channel, payload.getValue().getBytes(StandardCharsets.UTF_8)
        ), null);
        verifyNoInteractions(handler);
    }

    @Test
    @DisplayName("다른 인스턴스 신호를 받으면 해당 방의 커밋된 메시지만 조회한다")
    void refreshesSignaledRoom() {
        byte[] channel = ChatRedisRelay.CHANNEL.getBytes(StandardCharsets.UTF_8);
        relay.onMessage(new DefaultMessage(channel,
            ("other:901:" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8)), null);

        verify(handler).refreshRoom(901L);
    }

    @Test
    @DisplayName("잘못된 Redis 신호는 채팅방 조회에 사용하지 않는다")
    void ignoresMalformedSignal() {
        byte[] channel = ChatRedisRelay.CHANNEL.getBytes(StandardCharsets.UTF_8);
        relay.onMessage(new DefaultMessage(channel, "invalid".getBytes(StandardCharsets.UTF_8)), null);

        verifyNoInteractions(handler);
    }

    @Test
    @DisplayName("Redis 신호 예약 거부는 저장된 메시지 응답을 실패시키지 않는다")
    void rejectedPublishFallsBackToPolling() {
        TaskExecutor executor = mock(TaskExecutor.class);
        doThrow(new RejectedExecutionException("full")).when(executor).execute(any(Runnable.class));
        ChatRedisRelay rejectedRelay = new ChatRedisRelay(redisTemplate, handler, executor);

        assertDoesNotThrow(() -> rejectedRelay.publishCommittedMessage(
            new ChatMessageCreatedEvent(901L, mock(MessageResponse.class))
        ));
        verifyNoInteractions(redisTemplate, handler);
    }
}
