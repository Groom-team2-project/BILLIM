package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ChatRedisRelay implements MessageListener {

    public static final String CHANNEL = "billim:chat:committed";
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("chat.relay.diagnostics");

    private final StringRedisTemplate redisTemplate;
    private final ChatWebSocketHandler webSocketHandler;
    private final TaskExecutor deliveryExecutor;
    private final String instanceId = UUID.randomUUID().toString();

    public ChatRedisRelay(
        StringRedisTemplate redisTemplate,
        ChatWebSocketHandler webSocketHandler,
        @Qualifier("chatDeliveryExecutor") TaskExecutor deliveryExecutor
    ) {
        this.redisTemplate = redisTemplate;
        this.webSocketHandler = webSocketHandler;
        this.deliveryExecutor = deliveryExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishCommittedMessage(ChatMessageCreatedEvent event) {
        long queuedAt = System.nanoTime();
        try {
            deliveryExecutor.execute(() -> {
                try {
                    long startedAt = System.nanoTime();
                    long publishedAt = System.currentTimeMillis();
                    redisTemplate.convertAndSend(
                        CHANNEL, instanceId + ":" + event.roomId() + ":" + publishedAt
                    );
                    if (DIAGNOSTICS) {
                        log.info("채팅 중계 계측: 구간=발행 roomId={} 대기Ms={} 발행Ms={}",
                            event.roomId(), elapsedMs(queuedAt, startedAt),
                            elapsedMs(startedAt, System.nanoTime()));
                    }
                } catch (Exception exception) {
                    // DB 재확인 작업이 누락된 신호를 복구한다.
                    log.warn("채팅 Redis 신호 발행 실패: roomId={}", event.roomId(), exception);
                }
            });
        } catch (Exception exception) {
            log.warn("채팅 Redis 신호 예약 실패: roomId={}", event.roomId(), exception);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String value = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            int separator = value.indexOf(':');
            if (separator < 1) {
                throw new NumberFormatException("missing instanceId");
            }
            if (value.substring(0, separator).equals(instanceId)) {
                return;
            }
            int timestampSeparator = value.indexOf(':', separator + 1);
            if (timestampSeparator < 0) {
                throw new NumberFormatException("missing timestamp");
            }
            long roomId = Long.parseLong(value.substring(separator + 1, timestampSeparator));
            long publishedAt = Long.parseLong(value.substring(timestampSeparator + 1));
            if (roomId < 1) {
                throw new NumberFormatException("roomId must be positive");
            }
            long receivedAt = System.currentTimeMillis();
            long refreshStartedAt = System.nanoTime();
            webSocketHandler.refreshRoom(roomId);
            if (DIAGNOSTICS) {
                log.info("채팅 중계 계측: 구간=수신 roomId={} 신호Ms={} 재조회Ms={}",
                    roomId, receivedAt - publishedAt,
                    elapsedMs(refreshStartedAt, System.nanoTime()));
            }
        } catch (NumberFormatException exception) {
            log.warn("채팅 Redis 신호 형식 오류");
        } catch (Exception exception) {
            log.warn("채팅 Redis 신호 처리 실패: roomId={}", value, exception);
        }
    }

    private static double elapsedMs(long start, long end) {
        return (end - start) / 1_000_000.0;
    }
}
