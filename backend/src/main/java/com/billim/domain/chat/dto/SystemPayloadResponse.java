package com.billim.domain.chat.dto;

import java.time.Instant;

public record SystemPayloadResponse(
    String eventType,
    long aggregateVersion,
    Instant occurredAt,
    String label
) {
}
