package com.billim.domain.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(name = "chat_participants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "last_read_sequence", nullable = false)
    private long lastReadSequence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatParticipant join(Long roomId, Long memberId, Instant now) {
        Objects.requireNonNull(roomId);
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(now);

        ChatParticipant participant = new ChatParticipant();
        participant.roomId = roomId;
        participant.memberId = memberId;
        participant.lastReadSequence = 0;
        participant.createdAt = now;
        participant.updatedAt = now;
        return participant;
    }

    public void advanceReadSequence(long requestedSequence, long latestSequence, Instant now) {
        Objects.requireNonNull(now);

        if (requestedSequence < 0 || requestedSequence > latestSequence) {
            throw new IllegalArgumentException("읽은 위치가 올바르지 않습니다.");
        }
        if (requestedSequence > lastReadSequence) {
            lastReadSequence = requestedSequence;
            updatedAt = now;
        }
    }
}
