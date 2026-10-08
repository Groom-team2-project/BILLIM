package com.billim.domain.chat.repository;

import com.billim.domain.chat.entity.ChatRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ChatRoom room where room.id = :roomId")
    Optional<ChatRoom> findLockedById(@Param("roomId") Long roomId);
}
