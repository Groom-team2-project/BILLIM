package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.RentalStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RentalStatusHistoryRepository extends JpaRepository<RentalStatusHistory, Long> {
    List<RentalStatusHistory> findByRentalIdOrderByRentalVersionAsc(Long rentalId);
}
