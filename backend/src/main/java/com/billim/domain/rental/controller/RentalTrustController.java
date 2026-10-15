package com.billim.domain.rental.controller;

import com.billim.domain.rental.dto.TrustMetricsResponse;
import com.billim.domain.rental.service.RentalTrustService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class RentalTrustController {
    private final RentalTrustService trust;

    @GetMapping("/{memberId}/trust")
    public TrustMetricsResponse get(@PathVariable @Positive Long memberId,
                                    @AuthenticationPrincipal LoginMember viewer) {
        return trust.get(memberId, viewer == null ? null : viewer.memberId());
    }
}
