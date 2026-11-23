package com.billim.domain.chat.repository;

import com.billim.domain.rental.entity.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.Instant;
import com.billim.domain.rental.entity.RentalStatus;

public interface ChatRoomRentalReadRepository extends JpaRepository<Rental, Long> {

    @Query("""
        select rental from Rental rental
        where rental.itemId = :itemId
          and rental.ownerId = :ownerId
          and rental.borrowerId = :requesterId
          and (rental.chatRoomId is null or rental.chatRoomId = :roomId)
        """)
    List<Rental> findForRoomOpening(@Param("itemId") Long itemId,
                                    @Param("ownerId") Long ownerId,
                                    @Param("requesterId") Long requesterId,
                                    @Param("roomId") Long roomId);

    /** 현재 유효한 양측 제재보다 먼저 승인된 미완료 거래만 정리 대화에 허용. */
    @Query("""
        select rental.id from Rental rental
        where rental.id in :rentalIds
          and rental.status in :statuses
          and not exists (
            select sanction.id from Sanction sanction
            where (sanction.memberId = rental.ownerId or sanction.memberId = rental.borrowerId)
              and sanction.revokedAt is null
              and sanction.startsAt <= :now
              and (sanction.endsAt is null or sanction.endsAt > :now)
              and (rental.approvedAt is null or rental.approvedAt >= sanction.startsAt)
          )
        """)
    List<Long> findAllowedRentalIds(@Param("rentalIds") List<Long> rentalIds,
                                    @Param("statuses") List<RentalStatus> statuses,
                                    @Param("now") Instant now);

    @Query("""
        select rental from Rental rental, ChatRoom room
        where room.id in :roomIds
          and (room.ownerId = :memberId or room.requesterId = :memberId)
          and rental.chatRoomId = room.id
          and rental.itemId = room.itemId
          and rental.ownerId = room.ownerId
          and rental.borrowerId = room.requesterId
        order by rental.createdAt desc, rental.id desc
        """)
    List<Rental> findLinkedInRooms(@Param("roomIds") List<Long> roomIds,
                                   @Param("memberId") Long memberId);

    @Query("""
        select rental
        from Rental rental
        where rental.chatRoomId = :roomId
          and rental.itemId = :itemId
          and rental.ownerId = :ownerId
          and rental.borrowerId = :requesterId
        order by rental.createdAt desc, rental.id desc
        """)
    List<Rental> findLinked(
        @Param("roomId") Long roomId,
        @Param("itemId") Long itemId,
        @Param("ownerId") Long ownerId,
        @Param("requesterId") Long requesterId
    );

    @Query("""
    select case when count(rental) > 0 then true else false end
    from Rental rental
    where rental.id = :rentalId
      and rental.chatRoomId = :roomId
      and rental.itemId = :itemId
      and rental.ownerId = :ownerId
      and rental.borrowerId = :requesterId
    """)
    boolean existsLinked(
        @Param("rentalId") Long rentalId,
        @Param("roomId") Long roomId,
        @Param("itemId") Long itemId,
        @Param("ownerId") Long ownerId,
        @Param("requesterId") Long requesterId
    );
}
