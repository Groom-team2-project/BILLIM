package com.billim.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendMessageRequest(
    @NotNull UUID clientMessageId,
    @NotBlank @Size(max = 2000) String body,
    String rentalId
) {
}
