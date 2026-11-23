package com.billim.domain.rental.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import com.billim.domain.rental.entity.AppointmentKind;
import com.billim.domain.rental.entity.AppointmentSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSlot s where s.id = :slotId")
    Optional<AppointmentSlot> findByIdForUpdate(Long slotId);

    @Query("select s.rentalId from AppointmentSlot s where s.id = :slotId")
    Optional<Long> findRentalId(Long slotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppointmentSlot> findByRentalIdAndKind(Long rentalId,
            AppointmentKind kind);

    List<AppointmentSlot> findByRentalId(Long rentalId);
}
