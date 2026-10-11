package com.billim.domain.chat.dto;

import com.billim.domain.chat.entity.ChatMessage;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(
            String.valueOf(message.getId()),
            String.valueOf(message.getRoomId()),
            message.getSequence(),
            message.getType().name(),
            nullableId(message.getSenderId()),
            message.getBody(),
            nullableId(message.getRentalId()),
            null,
            message.getClientMessageId() == null
                ? null
                : UUID.fromString(message.getClientMessageId()),
            null,
            message.getCreatedAt()
        );
    }

    private static String nullableId(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
