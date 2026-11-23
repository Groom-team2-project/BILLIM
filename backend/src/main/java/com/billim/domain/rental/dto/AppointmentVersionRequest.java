package com.billim.domain.rental.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AppointmentVersionRequest(
    @NotNull @PositiveOrZero Long expectedVersion
) {
}
