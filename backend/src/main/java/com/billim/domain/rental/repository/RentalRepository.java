package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.Rental;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.billim.domain.rental.entity.RentalStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;

public interface RentalRepository extends JpaRepository<Rental, Long> {

    boolean existsByItemIdAndStatusAndDueAtLessThanEqual(Long itemId, RentalStatus status, Instant now);

    @Query("""
            select (count(r) > 0) from Rental r
            where r.itemId = :itemId and r.status in :statuses
              and r.startDate <= :endDate and r.endDate >= :startDate
            """)
    boolean hasOccupiedDates(Long itemId, Collection<RentalStatus> statuses,
                             LocalDate startDate, LocalDate endDate);

    @Query("""
            select (count(r) > 0) from Rental r
            where r.itemId = :itemId and r.borrowerId = :borrowerId
              and r.status = com.billim.domain.rental.entity.RentalStatus.REQUESTED
              and r.startDate <= :endDate and r.endDate >= :startDate
            """)
    boolean hasPendingRequest(Long itemId, Long borrowerId, LocalDate startDate, LocalDate endDate);
}
