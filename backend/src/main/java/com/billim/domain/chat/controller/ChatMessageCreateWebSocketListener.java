package com.billim.domain.chat.controller;


import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatMessageCreateWebSocketListener {

    private final ChatWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(ChatMessageCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.message());
            webSocketHandler.broadcast(event.roomId(), payload);
        } catch (Exception exception) {
            log.warn("채팅 실시간 이벤트 처리 실패: roomId={}, messageId={}",
                event.roomId(),
                event.message().id(),
                exception);
        }
    }
}
