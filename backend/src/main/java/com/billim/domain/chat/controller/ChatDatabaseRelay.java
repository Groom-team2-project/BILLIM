package com.billim.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatDatabaseRelay {

    private final ChatWebSocketHandler webSocketHandler;

    @Scheduled(fixedDelayString = "${billim.chat.delivery.poll-interval-ms:250}")
    public void deliverCommittedMessages() {
        for (Long roomId : webSocketHandler.activeRoomIds()) {
            try {
                webSocketHandler.refreshRoom(roomId);
            } catch (Exception exception) {
                log.warn("채팅 DB 전달 재확인 실패: roomId={}", roomId, exception);
            }
        }
    }
}
