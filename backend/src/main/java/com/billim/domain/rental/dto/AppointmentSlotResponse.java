package com.billim.domain.rental.dto;

import com.billim.domain.rental.entity.AppointmentKind;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppointmentSlotResponse(
        String id, String rentalId, AppointmentKind kind, long version,
        RentalDetailStatusResponse.AppointmentProposal confirmedProposal,
        RentalDetailStatusResponse.AppointmentProposal pendingProposal,
        List<String> allowedActions
) {
}
