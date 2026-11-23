package com.billim.domain.rental.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RentalAvailabilityResponse(String itemId, LocalDate availableStartDate, LocalDate availableEndDate,
                                         List<DateRange> occupiedRanges, boolean overdueBlocked,
                                         Instant checkedAt, Boolean selectedRangeAvailable) {
    public record DateRange(LocalDate startDate, LocalDate endDate) { }
}
