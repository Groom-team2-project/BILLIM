package com.billim.domain.rental.controller;

import com.billim.domain.rental.dto.AppointmentSlotsResponse;
import com.billim.domain.rental.service.AppointmentService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService appointmentService;

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

    /* 제안·수락·철회 구현 전 임시 보관
    //제안
    @PostMapping("/rentals/{rentalId}/appointments/{kind}/proposals")
    public ResponseEntity<AppointmentSlotResponse> proposalAppointment(
        @Valid @RequestBody ProposeAppointmentRequest request
    )

    //수락
    @PostMapping("/appointments/{proposalId}/accept")
    public ResponseEntity<AppointmentSlotResponse> acceptAppointment(
        @Valid @RequestBody AppointmentVersionRequest request
    )

    //철회
    @PostMapping("/appointments/{proposalId}/withdraw")
    public ResponseEntity<AppointmentSlotResponse> withdrawAppointment(
        @Valid @RequestBody AppointmentVersionRequest request
    )
    */
}
