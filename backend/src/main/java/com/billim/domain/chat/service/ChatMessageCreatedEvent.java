package com.billim.domain.chat.service;

public record ChatMessageCreatedEvent(
    Long messageId,
    Long roomId
) {
}
