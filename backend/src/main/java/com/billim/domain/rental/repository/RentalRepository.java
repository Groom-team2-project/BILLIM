package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.AppointmentProposal;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    interface Participants {
        Long getItemId();
        Long getOwnerId();
        Long getBorrowerId();
    }

    // 회원·물건 잠금 순서 확보용 불변 식별자 조회. 엔티티를 미리 로딩하지 않음.
    @Query("select r.itemId as itemId, r.ownerId as ownerId, r.borrowerId as borrowerId from Rental r where r.id = :rentalId")
    Optional<Participants> findParticipants(Long rentalId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rental r where r.id = :rentalId")
    Optional<Rental> findByIdForUpdate(Long rentalId);

    // 물건 잠금 획득 후 최신 커밋 상태를 읽기 위한 잠금 조회.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Rental r where r.itemId = :itemId and r.status in :statuses
              and r.startDate <= :endDate and r.endDate >= :startDate order by r.id
            """)
    List<Rental> findOccupiedForUpdate(Long itemId, Collection<RentalStatus> statuses,
                                      LocalDate startDate, LocalDate endDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Rental> findByItemIdAndStatusOrderById(Long itemId, RentalStatus status);

    @Query("""
            select r from Rental r where r.itemId = :itemId and r.status in :statuses
              and r.startDate <= :endDate and r.endDate >= :startDate order by r.startDate, r.endDate
            """)
    List<Rental> findOccupied(Long itemId, Collection<RentalStatus> statuses,
                              LocalDate startDate, LocalDate endDate);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select p from AppointmentSlot s join AppointmentProposal p
              on p.id = s.confirmedProposalId and p.slotId = s.id
            where s.rentalId = :rentalId
              and s.kind = com.billim.domain.rental.entity.AppointmentKind.RETURN
              and p.status = com.billim.domain.rental.entity.AppointmentProposalStatus.CONFIRMED
            """)
    Optional<AppointmentProposal> findConfirmedReturnProposal(Long rentalId);

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
