package com.billim.domain.chat.controller;

import com.billim.domain.chat.service.ChatMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatMessageCreateWebSocketListener {

    private final ChatWebSocketHandler webSocketHandler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCreated(ChatMessageCreatedEvent event) {
        webSocketHandler.refreshRoom(event.roomId());
    }
}
