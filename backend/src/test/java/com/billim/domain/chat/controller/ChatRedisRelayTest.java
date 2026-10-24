package com.billim.domain.chat.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

class ChatRedisRelayTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    private final ChatRedisRelay relay = new ChatRedisRelay(redisTemplate, handler);

    @Test
    @DisplayName("커밋된 메시지는 내용 없이 채팅방 ID만 발행한다")
    void publishesRoomSignal() {
        relay.publishCommittedMessage(new ChatMessageCreatedEvent(901L, mock(MessageResponse.class)));

        verify(redisTemplate).convertAndSend(ChatRedisRelay.CHANNEL, "901");
    }

    @Test
    @DisplayName("다른 인스턴스 신호를 받으면 해당 방의 커밋된 메시지만 조회한다")
    void refreshesSignaledRoom() {
        byte[] channel = ChatRedisRelay.CHANNEL.getBytes(StandardCharsets.UTF_8);
        relay.onMessage(new DefaultMessage(channel, "901".getBytes(StandardCharsets.UTF_8)), null);

        verify(handler).refreshRoom(901L);
    }

    @Test
    @DisplayName("잘못된 Redis 신호는 채팅방 조회에 사용하지 않는다")
    void ignoresMalformedSignal() {
        byte[] channel = ChatRedisRelay.CHANNEL.getBytes(StandardCharsets.UTF_8);
        relay.onMessage(new DefaultMessage(channel, "invalid".getBytes(StandardCharsets.UTF_8)), null);

        verifyNoInteractions(handler);
    }
}
