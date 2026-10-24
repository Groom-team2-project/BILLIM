package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatRedisRelay implements MessageListener {

    public static final String CHANNEL = "billim:chat:committed";

    private final StringRedisTemplate redisTemplate;
    private final ChatWebSocketHandler webSocketHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCommittedMessage(ChatMessageCreatedEvent event) {
        try {
            redisTemplate.convertAndSend(CHANNEL, event.roomId().toString());
        } catch (Exception exception) {
            // DB 재확인 작업이 누락된 신호를 복구한다.
            log.warn("채팅 Redis 신호 발행 실패: roomId={}", event.roomId(), exception);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String value = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            long roomId = Long.parseLong(value);
            if (roomId < 1) {
                throw new NumberFormatException("roomId must be positive");
            }
            webSocketHandler.refreshRoom(roomId);
        } catch (NumberFormatException exception) {
            log.warn("채팅 Redis 신호 형식 오류");
        } catch (Exception exception) {
            log.warn("채팅 Redis 신호 처리 실패: roomId={}", value, exception);
        }
    }
}
