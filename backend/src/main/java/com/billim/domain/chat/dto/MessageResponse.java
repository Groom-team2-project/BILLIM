package com.billim.domain.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    String id,
    String roomId,
    long sequence,
    String type,
    String senderId,
    String body,
    String rentalId,
    String appointmentProposalId,
    UUID clientMessageId,
    SystemPayloadResponse system,
    Instant createdAt
) {
}
