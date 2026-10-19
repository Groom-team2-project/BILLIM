package com.billim.domain.rental.controller;

import com.billim.domain.rental.dto.AppointmentSlotsResponse;
import com.billim.domain.rental.service.AppointmentService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import com.billim.domain.rental.dto.AppointmentSlotResponse;
import com.billim.domain.rental.dto.ProposeAppointmentRequest;
import com.billim.domain.rental.dto.AppointmentVersionRequest;
import com.billim.domain.rental.entity.AppointmentKind;
import com.billim.domain.rental.service.AppointmentCommandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService appointmentService;

    private final AppointmentCommandService commands;

    //조회
    @GetMapping("/rentals/{rentalId}/appointments")
    public ResponseEntity<AppointmentSlotsResponse> getAppointments(
        @PathVariable @Positive Long rentalId,
        @AuthenticationPrincipal LoginMember member
    ) {
        return ResponseEntity.ok(
            appointmentService.getAppointments(rentalId, member)
        );
    }

    @PostMapping("/rentals/{rentalId}/appointments/{kind}/proposals")
    public ResponseEntity<AppointmentSlotResponse> propose(
            @PathVariable @Positive Long rentalId,
            @PathVariable AppointmentKind kind,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody ProposeAppointmentRequest request,
            @AuthenticationPrincipal LoginMember member) {
        return ResponseEntity.status(201).body(commands.propose(rentalId, kind, request, key, member));
    }

    @PostMapping("/appointments/{proposalId}/accept")
    public ResponseEntity<AppointmentSlotResponse> accept(
            @PathVariable @Positive Long proposalId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AppointmentVersionRequest request,
            @AuthenticationPrincipal LoginMember member) {
        return ResponseEntity.ok(commands.accept(proposalId, request, key, member));
    }

    @PostMapping("/appointments/{proposalId}/withdraw")
    public ResponseEntity<AppointmentSlotResponse> withdraw(
            @PathVariable @Positive Long proposalId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AppointmentVersionRequest request,
            @AuthenticationPrincipal LoginMember member) {
        return ResponseEntity.ok(commands.withdraw(proposalId, request, key, member));
    }
}
