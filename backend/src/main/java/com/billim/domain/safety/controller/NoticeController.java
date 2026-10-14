package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.NoticePageResponse;
import com.billim.domain.safety.dto.NoticeResponse;
import com.billim.domain.safety.service.NoticeService;
import com.billim.global.security.LoginMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 동네 공지 조회 (API 명세 14.1, 14.2). 유효 소속 회원 또는 ADMIN */
@RestController
@RequestMapping("/api/v1")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** E_063 동네 공지 목록. 고정 먼저 최신순 */
    @GetMapping("/communities/{communityId}/notices")
    public NoticePageResponse findCommunityNotices(
            @PathVariable @Positive Long communityId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        return noticeService.findCommunityNotices(
                SafetyRequests.memberId(member), SafetyRequests.isAdmin(member),
                communityId, page, size);
    }

    /** E_064 공지 상세. 삭제된 공지는 404 */
    @GetMapping("/notices/{noticeId}")
    public NoticeResponse findNotice(
            @PathVariable @Positive Long noticeId,
            @AuthenticationPrincipal LoginMember member) {
        return noticeService.findNotice(
                SafetyRequests.memberId(member), SafetyRequests.isAdmin(member), noticeId);
    }
}
