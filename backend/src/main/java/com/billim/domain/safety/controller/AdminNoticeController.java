package com.billim.domain.safety.controller;

import com.billim.domain.safety.dto.CreateNoticeRequest;
import com.billim.domain.safety.dto.NoticePageResponse;
import com.billim.domain.safety.dto.NoticeResponse;
import com.billim.domain.safety.dto.UpdateNoticeRequest;
import com.billim.domain.safety.service.NoticeService;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공지 관리 (API 명세 14.3 ~ 14.6). ADMIN만 호출한다 */
@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

    private final NoticeService noticeService;

    public AdminNoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    /** E_065 관리자 공지 목록. 동네 필터는 선택 */
    @GetMapping
    public NoticePageResponse findNotices(
            @RequestParam(required = false) @Positive Long communityId,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int size,
            @AuthenticationPrincipal LoginMember member) {
        SafetyRequests.adminId(member);
        return noticeService.findAllNotices(communityId, page, size);
    }

    /** E_066 공지 작성. 활성 커뮤니티만 */
    @PostMapping
    public ResponseEntity<NoticeResponse> create(
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = SafetyRequests.UUID_PATTERN) String idempotencyKey,
            @Valid @RequestBody CreateNoticeRequest request,
            @AuthenticationPrincipal LoginMember member) {
        NoticeResponse response = noticeService.create(SafetyRequests.adminId(member), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** E_067 공지 수정·고정. version 검사 후 전체 교체 */
    @PutMapping("/{noticeId}")
    public NoticeResponse update(
            @PathVariable @Positive Long noticeId,
            @Valid @RequestBody UpdateNoticeRequest request,
            @AuthenticationPrincipal LoginMember member) {
        SafetyRequests.adminId(member);
        return noticeService.update(noticeId, request);
    }

    /** E_068 공지 삭제. version 검사 후 논리 삭제 */
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Long noticeId,
            @RequestParam @NotNull @PositiveOrZero Long expectedVersion,
            @AuthenticationPrincipal LoginMember member) {
        SafetyRequests.adminId(member);
        noticeService.delete(noticeId, expectedVersion);
        return ResponseEntity.noContent().build();
    }
}
