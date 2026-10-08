package com.billim.domain.rental.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CancelRentalRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {
}
