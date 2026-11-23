package com.billim.domain.rental.dto;

import java.util.List;

public record RentalCountStatusResponse(
        RentalCounts borrowed, RentalCounts lent, List<RentalSummaryResponse> nextRentals
) {
    public record RentalCounts(
            int requested, int approved, int active, int overdue,
            int returned, int rejected, int canceled, int expired
    ) {
    }
}
