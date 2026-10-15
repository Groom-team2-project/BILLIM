package com.billim.domain.rental.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record ProposeAppointmentRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull Instant scheduledAt,
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    @DecimalMax("9223372036854775807") String placeId
) {
}
