package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {
    List<AppointmentSlot> findByRentalId(Long rentalId);
}
