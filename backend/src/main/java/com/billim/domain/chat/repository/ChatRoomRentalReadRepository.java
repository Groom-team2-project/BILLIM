package com.billim.domain.chat.repository;

import com.billim.domain.rental.entity.Rental;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRentalReadRepository extends JpaRepository<Rental, Long> {

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
