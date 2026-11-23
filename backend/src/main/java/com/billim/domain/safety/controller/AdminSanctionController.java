package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.RevokeSanctionRequest;
import com.billim.domain.safety.dto.SanctionPageResponse;
import com.billim.domain.safety.dto.SanctionResponse;
import com.billim.domain.safety.service.SanctionService;
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

/** 제재 이력과 해제 (API 명세 13.4, 13.5). ADMIN만 호출한다 */
@RestController
@RequestMapping("/api/v1/admin/sanctions")
public class AdminSanctionController {

    private final SanctionService sanctionService;

    public AdminSanctionController(SanctionService sanctionService) {
        this.sanctionService = sanctionService;
    }

    /** E_061 제재 이력 조회. 만료·해제 포함 */
    @GetMapping
    public SanctionPageResponse findSanctions(
            @RequestParam(required = false) @Positive Long memberId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        SafetyRequests.adminId(member);
        return sanctionService.findSanctions(memberId, page, size);
    }

    /** E_062 제재 해제. 사유 필수, 반복 호출에 안전하다 */
    @PostMapping("/{sanctionId}/revoke")
    public SanctionResponse revoke(
            @PathVariable @Positive Long sanctionId,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = SafetyRequests.UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody RevokeSanctionRequest request,
            @AuthenticationPrincipal LoginMember member) {
        return sanctionService.revoke(SafetyRequests.adminId(member), sanctionId, request.reason());
    }
}
