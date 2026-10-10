package com.billim.domain.rental.dto;

import com.billim.domain.rental.entity.RentalStatus;

import com.billim.domain.item.dto.MemberSummaryResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RentalSummaryResponse(
        String id, String itemId, String itemTitle,
        MemberSummaryResponse owner, MemberSummaryResponse borrower,
        LocalDate startDate, LocalDate endDate, RentalStatus status,
        boolean overdue, int dDay, String placeName, long version,
        List<String> allowedActions, Instant createdAt
) {
}
