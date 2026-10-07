package com.billim.domain.chat.repository;

import com.billim.domain.chat.entity.ChatParticipant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByRoomIdAndMemberId(Long roomId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select participant from ChatParticipant participant "
            + "where participant.roomId = :roomId and participant.memberId = :memberId")
    Optional<ChatParticipant> findLockedByRoomIdAndMemberId(
            @Param("roomId") Long roomId,
            @Param("memberId") Long memberId
    );

    Optional<ChatParticipant> findByRoomIdAndMemberIdNot(
        Long roomId,
        Long memberId
    );
}
