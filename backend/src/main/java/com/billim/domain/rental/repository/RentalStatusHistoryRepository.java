package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.RentalStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;

public interface RentalStatusHistoryRepository extends JpaRepository<RentalStatusHistory, Long> {
    List<RentalStatusHistory> findByRentalIdOrderByRentalVersionAsc(Long rentalId);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select h from RentalStatusHistory h where h.rentalId = :rentalId order by h.rentalVersion")
    List<RentalStatusHistory> findCurrentHistory(Long rentalId);
}
