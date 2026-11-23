package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.CreateReportRequest;
import com.billim.domain.safety.dto.ReportPageResponse;
import com.billim.domain.safety.dto.ReportResponse;
import com.billim.domain.safety.service.ReportService;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 신고 접수와 내 신고 조회 (API 명세 12.4 ~ 12.6) */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** E_055 신고 접수 */
    @PostMapping("/reports")
    public ResponseEntity<ReportResponse> create(
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = SafetyRequests.UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody CreateReportRequest request,
            @AuthenticationPrincipal LoginMember member) {
        ReportResponse response = reportService.create(SafetyRequests.memberId(member), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** E_056 내 신고 목록 */
    @GetMapping("/members/me/reports")
    public ReportPageResponse findMyReports(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        return reportService.findMyReports(SafetyRequests.memberId(member), page, size);
    }

    /** E_057 내 신고 결과 상세. 신고자만 조회한다 */
    @GetMapping("/reports/{reportId}")
    public ReportResponse findMyReport(
            @PathVariable @Positive Long reportId,
            @AuthenticationPrincipal LoginMember member) {
        return reportService.findMyReport(SafetyRequests.memberId(member), reportId);
    }
}
