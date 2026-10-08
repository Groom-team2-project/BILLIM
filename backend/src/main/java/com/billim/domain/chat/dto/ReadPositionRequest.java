package com.billim.domain.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReadPositionRequest(
    @NotNull @PositiveOrZero Long lastReadSequence
) {
}
