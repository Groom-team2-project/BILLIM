package com.billim.domain.rental.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import com.billim.domain.rental.entity.AppointmentProposal;
import com.billim.domain.rental.entity.AppointmentProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AppointmentProposalRepository extends JpaRepository<AppointmentProposal, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AppointmentProposal p where p.id = :proposalId")
    Optional<AppointmentProposal> findByIdForUpdate(Long proposalId);

    @Query("select p.slotId from AppointmentProposal p where p.id = :proposalId")
    Optional<Long> findSlotId(Long proposalId);

    List<AppointmentProposal> findBySlotIdAndStatus(Long slotId, AppointmentProposalStatus status);
}
