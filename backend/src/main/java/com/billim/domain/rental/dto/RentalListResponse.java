package com.billim.domain.rental.dto;

import java.util.List;

public record RentalListResponse(
        List<RentalSummaryResponse> items, int page, int size,
        long totalElements, boolean hasNext
) {
}
