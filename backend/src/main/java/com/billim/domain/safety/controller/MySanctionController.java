package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.MySanctionPageResponse;
import com.billim.domain.safety.service.MySanctionService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내가 받은 운영 조치 내역 (API 명세 12.7).
 *
 * <p>본인만 조회한다. 제재 상태여도 자기 조치는 볼 수 있어야 한다 — 제한된 이유를 모르면
 * 이의를 제기할 수도 없다. 관리자용 제재 이력(E_061)과 달리 다른 회원은 조회할 수 없다.
 */
@RestController
@RequestMapping("/api/v1/members/me/sanctions")
public class MySanctionController {

    private final MySanctionService mySanctionService;

    public MySanctionController(MySanctionService mySanctionService) {
        this.mySanctionService = mySanctionService;
    }

    /** E_072 내가 받은 운영 조치 내역 */
    @GetMapping
    public MySanctionPageResponse findMySanctions(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        return mySanctionService.findMySanctions(SafetyRequests.memberId(member), page, size);
    }
}
