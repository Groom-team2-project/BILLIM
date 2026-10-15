package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.AppointmentProposal;
import com.billim.domain.rental.entity.AppointmentProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentProposalRepository extends JpaRepository<AppointmentProposal, Long> {
    List<AppointmentProposal> findBySlotIdAndStatus(Long slotId, AppointmentProposalStatus status);
}
