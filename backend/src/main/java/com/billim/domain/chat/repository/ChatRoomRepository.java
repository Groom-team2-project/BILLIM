package com.billim.domain.chat.repository;

import com.billim.domain.chat.entity.ChatRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from ChatRoom room where room.id = :roomId")
    Optional<ChatRoom> findLockedById(@Param("roomId") Long roomId);

    Optional<ChatRoom> findByItemIdAndRequesterId(Long itemId, Long requesterId);

    @Query(value = """
        select room
        from ChatRoom room
        where room.ownerId = :memberId
        or room.requesterId = :memberId
        order by coalesce(room.lastMessageAt, room.createdAt) desc,
            room.id desc
    """,
            countQuery = """
                select count(room)
                from ChatRoom room
                where room.ownerId = :memberId
                or room.requesterId = :memberId
            """
    )
    Page<ChatRoom> findParticipatingRooms(
        @Param("memberId") Long memberId,
        Pageable pageable
    );
}
