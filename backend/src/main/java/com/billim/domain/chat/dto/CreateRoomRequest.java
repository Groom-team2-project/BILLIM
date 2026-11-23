package com.billim.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(
    @NotBlank String itemId
) {
}
