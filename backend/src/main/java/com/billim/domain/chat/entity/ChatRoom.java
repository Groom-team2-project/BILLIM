package com.billim.domain.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@Entity
@Table(name = "chat_rooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "next_sequence", nullable = false)
    private long nextSequence;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChatRoom open(
        Long itemId,
        Long ownerId,
        Long requesterId,
        Instant now
    ) {
        Objects.requireNonNull(itemId);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(requesterId);
        Objects.requireNonNull(now);

        if (ownerId.equals(requesterId)) {
            throw new IllegalArgumentException("자신이 등록한 물건에 문의할 수 없습니다.");
        }

        ChatRoom room = new ChatRoom();
        room.itemId = itemId;
        room.ownerId = ownerId;
        room.requesterId = requesterId;
        room.nextSequence = 1;
        room.createdAt = now;
        room.updatedAt = now;
        return room;
    }

    public boolean isParticipant(Long memberId) {
        return ownerId.equals(memberId) || requesterId.equals(memberId);
    }

    // allocateSequence 메서드 사용시 Room의 Row에 락을 건 트랜잭션 내에서만 호출해야 합니다.
    public long allocateSequence(Instant now) {
        Objects.requireNonNull(now);

        long sequence = nextSequence;
        nextSequence++;
        lastMessageAt = now;
        updatedAt = now;
        return sequence;
    }
}
