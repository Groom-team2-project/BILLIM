package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.AdminStatisticsResponse;
import com.billim.domain.safety.service.AdminStatisticsService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 동네 운영 통계 (API 명세 13.6). ADMIN만 호출한다 */
@RestController
@RequestMapping("/api/v1/admin/statistics")
public class AdminStatisticsController {

    private final AdminStatisticsService adminStatisticsService;

    public AdminStatisticsController(AdminStatisticsService adminStatisticsService) {
        this.adminStatisticsService = adminStatisticsService;
    }

    /** E_069 동네 운영 통계. from/to는 KST 달력 날짜 양끝 포함, 최대 31일 */
    @GetMapping
    public AdminStatisticsResponse findStatistics(
            @RequestParam @NotNull @Positive Long communityId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal LoginMember member) {
        SafetyRequests.adminId(member);
        return adminStatisticsService.findStatistics(communityId, from, to);
    }
}
