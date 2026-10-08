package com.billim.domain.chat.repository;

import com.billim.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByRoomIdAndSenderIdAndClientMessageId(
            Long roomId,
            Long senderId,
            String clientMessageId
    );

    List<ChatMessage> findByRoomIdOrderBySequenceDesc(Long roomId, Pageable pageable);

    List<ChatMessage> findByRoomIdAndSequenceGreaterThanOrderBySequenceAsc(
            Long roomId,
            long afterSequence,
            Pageable pageable
    );

    List<ChatMessage> findByRoomIdAndSequenceLessThanOrderBySequenceDesc(
            Long roomId,
            long beforeSequence,
            Pageable pageable
    );

    @Query("""
        select count(message)
        from ChatMessage message, ChatParticipant participant
        where participant.memberId = :memberId
          and message.roomId = participant.roomId
          and message.sequence > participant.lastReadSequence
          and message.senderId <> :memberId
          and message.type = :type
        """)
    long countUnreadMessages(
        @Param("memberId") Long memberId,
        @Param("type") ChatMessage.Type type
    );
}
