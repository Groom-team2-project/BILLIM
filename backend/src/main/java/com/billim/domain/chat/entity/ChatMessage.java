package com.billim.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@Entity
@Table(name = "messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {
    public enum Type {
        TEXT,
        SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "sequence", nullable = false)
    private long sequence;

    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private Type type;

    @Column(name = "body", length = 2000)
    private String body;

    @Column(name = "rental_id")
    private Long rentalId;

    @Column(name = "client_message_id", length = 36, columnDefinition = "char(36)")
    private String clientMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatMessage text(
        Long roomId,
        long sequence,
        Long senderId,
        String body,
        Long rentalId,
        UUID clientMessageId,
        Instant now
    ) {
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(senderId);
        Objects.requireNonNull(clientMessageId);
        Objects.requireNonNull(now);

        if (sequence < 1 || body == null || body.isBlank() || body.length() > 2000) {
            throw new IllegalArgumentException("메세지 형식이 올바르지 않습니다.");
        }

        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.sequence = sequence;
        message.senderId = senderId;
        message.type = Type.TEXT;
        message.body = body;
        message.rentalId = rentalId;
        message.clientMessageId = clientMessageId.toString();
        message.createdAt = now;
        message.updatedAt = now;
        return message;
    }
}
