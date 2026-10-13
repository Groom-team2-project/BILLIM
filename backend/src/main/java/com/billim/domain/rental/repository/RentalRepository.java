package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RentalRepository extends JpaRepository<Rental, Long>, JpaSpecificationExecutor<Rental> {

    boolean existsByItemIdAndStatus(Long itemId, RentalStatus status);

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

    @Query("""
            select r from Rental r where r.itemId = :itemId and r.status in :statuses
              and r.startDate <= :endDate and r.endDate >= :startDate order by r.startDate, r.endDate
            """)
    List<Rental> findOccupied(Long itemId, Collection<RentalStatus> statuses,
                              LocalDate startDate, LocalDate endDate);

    interface StatusCount {
        RentalStatus getStatus();
        long getTotal();
    }

    @Query("""
            select r.status as status, count(r) as total from Rental r
            where (:owner = true and r.ownerId = :memberId)
               or (:owner = false and r.borrowerId = :memberId)
            group by r.status
            """)
    List<StatusCount> countStatuses(long memberId, boolean owner);

    @Query("""
            select count(r) from Rental r where r.status = com.billim.domain.rental.entity.RentalStatus.ACTIVE
              and r.dueAt <= :now
              and ((:owner = true and r.ownerId = :memberId) or (:owner = false and r.borrowerId = :memberId))
            """)
    long countOverdue(long memberId, boolean owner, Instant now);

    @Query("""
            select r from Rental r where (r.ownerId = :memberId or r.borrowerId = :memberId)
              and ((r.status = com.billim.domain.rental.entity.RentalStatus.APPROVED and r.startDate >= :today)
                or (r.status = com.billim.domain.rental.entity.RentalStatus.ACTIVE and r.endDate >= :today))
            order by case when r.status = com.billim.domain.rental.entity.RentalStatus.ACTIVE
                then r.endDate else r.startDate end, r.id
            """)
    List<Rental> findNextRentals(long memberId, LocalDate today, Pageable pageable);
}
