package com.billim.domain.rental.controller;

import com.billim.domain.rental.dto.RentalAvailabilityResponse;
import com.billim.domain.rental.service.RentalQueryService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/items")
public class RentalAvailabilityController {
    private final RentalQueryService queries;

    public RentalAvailabilityController(RentalQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{itemId}/availability")
    public RentalAvailabilityResponse getAvailability(@PathVariable @Positive Long itemId,
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @AuthenticationPrincipal LoginMember member) {
        return queries.getAvailability(itemId, from, to, startDate, endDate, member);
    }
}
