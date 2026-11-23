package com.billim.domain.rental.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record CreateRentalRequest(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
        @DecimalMax("9223372036854775807") String itemId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
