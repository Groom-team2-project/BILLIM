package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ChatMessageCreateWebSocketListener {

    private final ChatWebSocketHandler webSocketHandler;
    private final TaskExecutor deliveryExecutor;

    public ChatMessageCreateWebSocketListener(
        ChatWebSocketHandler webSocketHandler,
        @Qualifier("chatDeliveryExecutor") TaskExecutor deliveryExecutor
    ) {
        this.webSocketHandler = webSocketHandler;
        this.deliveryExecutor = deliveryExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(ChatMessageCreatedEvent event) {
        try {
            deliveryExecutor.execute(() -> {
                try {
                    webSocketHandler.broadcastCommittedMessage(event.roomId(), event.message());
                } catch (Exception exception) {
                    log.warn("채팅 로컬 전달 실패: roomId={}", event.roomId(), exception);
                }
            });
        } catch (Exception exception) {
            // DB 재확인 작업이 거부된 전달을 복구한다.
            log.warn("채팅 로컬 전달 예약 실패: roomId={}", event.roomId(), exception);
        }
    }
}
