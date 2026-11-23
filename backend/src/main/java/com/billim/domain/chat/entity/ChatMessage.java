package com.billim.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "appointment_proposal_id")
    private Long appointmentProposalId;

    @Column(name = "event_id")
    private Long eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "system_payload", columnDefinition = "json")
    private String systemPayload;

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

    public static ChatMessage system(
        Long roomId,
        long sequence,
        Long rentalId,
        Long appointmentProposalId,
        Long eventId,
        String systemPayload,
        Instant now
    ) {
        Objects.requireNonNull(now);

        if (roomId == null || roomId < 1
            || sequence < 1
            || rentalId == null || rentalId < 1
            || eventId == null || eventId < 1
            || appointmentProposalId != null && appointmentProposalId < 1
            || systemPayload == null || systemPayload.isBlank()) {
            throw new IllegalArgumentException("시스템 메시지 형식이 올바르지 않습니다.");
        }

        ChatMessage message = new ChatMessage();
        message.roomId = roomId;
        message.sequence = sequence;
        message.type = Type.SYSTEM;
        message.rentalId = rentalId;
        message.appointmentProposalId = appointmentProposalId;
        message.eventId = eventId;
        message.systemPayload = systemPayload;
        message.createdAt = now;
        message.updatedAt = now;
        return message;
    }
}
