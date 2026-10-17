package com.billim.domain.rental.dto;

import java.util.List;

public record AppointmentSlotsResponse(
        List<AppointmentSlotResponse> items
) {
}
