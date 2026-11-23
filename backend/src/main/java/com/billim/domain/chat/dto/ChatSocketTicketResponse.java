package com.billim.domain.chat.dto;

import java.time.Instant;

public record ChatSocketTicketResponse(String ticket, Instant expiresAt) {
    @Override
    public String toString() {
        return "ChatSocketTicketResponse[ticket=REDACTED, expiresAt=" + expiresAt + "]";
    }
}
