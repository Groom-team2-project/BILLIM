package com.billim.domain.rental.controller;

import com.billim.domain.rental.dto.CancelRentalRequest;
import com.billim.domain.rental.dto.CreateRentalRequest;
import com.billim.domain.rental.dto.RejectRentalRequest;
import com.billim.domain.rental.dto.RentalCountStatusResponse;
import com.billim.domain.rental.dto.RentalDetailStatusResponse;
import com.billim.domain.rental.dto.RentalListResponse;
import com.billim.domain.rental.dto.RentalRole;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.dto.RentalVersionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.billim.global.security.LoginMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/rentals")
public class RentalController {

    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    // 대여 요청 생성
    @PostMapping
    public ResponseEntity<RentalDetailStatusResponse> createRental(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody CreateRentalRequest request,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 대여 취소
    @PostMapping("/{rentalId}/cancel")
    public ResponseEntity<RentalDetailStatusResponse> cancelRental(
            @PathVariable @Positive Long rentalId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody CancelRentalRequest request,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 대여 승인
    @PostMapping("/{rentalId}/approve")
    public ResponseEntity<RentalDetailStatusResponse> approveRental(
            @PathVariable @Positive Long rentalId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody RentalVersionRequest request,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 대여 거절
    @PostMapping("/{rentalId}/reject")
    public ResponseEntity<RentalDetailStatusResponse> rejectRental(
            @PathVariable @Positive Long rentalId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody RejectRentalRequest request,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 대여 중
    @PostMapping("/{rentalId}/handover")
    public ResponseEntity<RentalDetailStatusResponse> handoverRental(
            @PathVariable @Positive Long rentalId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody RentalVersionRequest request,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 반납 확인
    @PostMapping("/{rentalId}/return")
    public ResponseEntity<RentalDetailStatusResponse> returnRental(
            @PathVariable @Positive Long rentalId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody RentalVersionRequest request,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 내 대여 목록 조회
    @GetMapping
    public ResponseEntity<RentalListResponse> getRentalList(
            @RequestParam RentalRole role,
            @RequestParam(required = false) RentalStatus status,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(required = false) @Positive Long itemId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 내 대여 상태별 건수 조회
    @GetMapping("/summary")
    public ResponseEntity<RentalCountStatusResponse> getRentalCountStatus(
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 내 거래 상세 상태 조회
    @GetMapping("/{rentalId}")
    public ResponseEntity<RentalDetailStatusResponse> getRentalDetailStatus(
            @PathVariable @Positive Long rentalId,
            @AuthenticationPrincipal LoginMember member) {
        throw notImplemented();
    }

    // 서비스 연결 전 실제 처리로 오인할 수 있는 성공 응답 방지
    private static ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "대여 서비스 미구현");
    }
}
