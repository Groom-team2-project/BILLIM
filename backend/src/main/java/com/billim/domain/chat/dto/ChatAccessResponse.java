package com.billim.domain.chat.dto;

import java.util.List;

public record ChatAccessResponse(
    boolean canSend,
    List<String> allowedRentalIds,
    String reason
) {
    public ChatAccessResponse {
        allowedRentalIds = List.copyOf(allowedRentalIds);
    }
}
