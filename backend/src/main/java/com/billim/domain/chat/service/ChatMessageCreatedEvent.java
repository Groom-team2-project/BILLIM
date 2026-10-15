package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.MessageResponse;

public record ChatMessageCreatedEvent(
    Long roomId,
    MessageResponse message
) {
}
