package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.AdminReportPageResponse;
import com.billim.domain.safety.dto.AdminReportResponse;
import com.billim.domain.safety.dto.ResolveReportRequest;
import com.billim.domain.safety.entity.ReportStatus;
import com.billim.domain.safety.service.AdminReportService;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 운영 신고 조회와 처리 (API 명세 13.1 ~ 13.3). ADMIN만 호출한다 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    public AdminReportController(AdminReportService adminReportService) {
        this.adminReportService = adminReportService;
    }

    /** E_058 운영 신고 목록 */
    @GetMapping
    public AdminReportPageResponse findReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) @Positive Long communityId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        SafetyRequests.adminId(member);
        return adminReportService.findReports(status, communityId, page, size);
    }

    /** E_059 신고 증거와 처리 상세. internalNote는 이 응답에만 담긴다 */
    @GetMapping("/{reportId}")
    public AdminReportResponse findReport(
            @PathVariable @Positive Long reportId,
            @AuthenticationPrincipal LoginMember member) {
        SafetyRequests.adminId(member);
        return adminReportService.findReport(reportId);
    }

    /** E_060 신고 처리·제재 결정 */
    @PostMapping("/{reportId}/resolve")
    public AdminReportResponse resolve(
            @PathVariable @Positive Long reportId,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = SafetyRequests.UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody ResolveReportRequest request,
            @AuthenticationPrincipal LoginMember member) {
        return adminReportService.resolve(SafetyRequests.adminId(member), reportId, request);
    }
}
