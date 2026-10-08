package com.billim.domain.rental.dto;

import com.billim.domain.rental.entity.RentalStatus;

import com.billim.domain.item.dto.PlaceResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RentalDetailStatusResponse(
        RentalSummaryResponse rental, String reason,
        Instant requestExpiresAt, Instant dueAt, Instant approvedAt,
        Instant handedOverAt, Instant returnedAt, String chatRoomId,
        List<History> history, List<AppointmentSlot> appointments
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record History(
            RentalStatus fromStatus, RentalStatus toStatus, String actorId,
            String reason, long rentalVersion, Instant createdAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentSlot(
            String id, String rentalId, String kind, long version,
            AppointmentProposal confirmedProposal, AppointmentProposal pendingProposal,
            List<String> allowedActions
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentProposal(
            String id, String slotId, String proposerId, String kind,
            Instant scheduledAt, PlaceResponse place, String status,
            String acceptedBy, Instant acceptedAt
    ) {
    }
}
