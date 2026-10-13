package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.RentalStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalStatusHistoryRepository extends JpaRepository<RentalStatusHistory, Long> {
}
