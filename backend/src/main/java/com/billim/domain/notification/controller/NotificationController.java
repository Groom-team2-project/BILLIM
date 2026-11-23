package com.billim.domain.notification.controller;

import com.billim.domain.notification.dto.NotificationPageResponse;
import com.billim.domain.notification.dto.NotificationResponse;
import com.billim.domain.notification.dto.ReadAllNotificationsRequest;
import com.billim.domain.notification.dto.UnreadCountResponse;
import com.billim.domain.notification.service.NotificationQueryService;
import com.billim.domain.notification.service.NotificationReadService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 API (API 명세 11장 D_048 ~ D_051) */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final String ID_PATTERN = "[1-9][0-9]{0,18}";

    private final NotificationQueryService queryService;
    private final NotificationReadService readService;

    public NotificationController(NotificationQueryService queryService,
                                  NotificationReadService readService) {
        this.queryService = queryService;
        this.readService = readService;
    }

    /** D_048 내 알림 목록. 본인 수신만, id DESC. cursor는 exclusive id */
    @GetMapping
    public NotificationPageResponse list(
            @RequestParam(required = false) @Pattern(regexp = ID_PATTERN) String cursor,
            @RequestParam(defaultValue = "20") @Positive @Max(50) int limit,
            @RequestParam(required = false) Boolean read,
            @AuthenticationPrincipal LoginMember member) {
        Long from = cursor == null ? null : Long.valueOf(cursor);
        return queryService.list(memberId(member), from, limit, read);
    }

    /** D_049 안 읽은 알림 수 */
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal LoginMember member) {
        return queryService.unreadCount(memberId(member));
    }

    /** D_050 알림 개별 읽음. 이미 읽었으면 최초 readAt 유지 */
    @PutMapping("/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable @Positive Long notificationId,
            @AuthenticationPrincipal LoginMember member) {
        return readService.markRead(memberId(member), notificationId);
    }

    /** D_051 기준 ID까지 모두 읽음. 기준 ID 이후 신규 알림은 미열람으로 남는다 */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(
            @Valid @RequestBody ReadAllNotificationsRequest request,
            @AuthenticationPrincipal LoginMember member) {
        readService.markAllRead(memberId(member), request.throughIdAsLong());
        return ResponseEntity.noContent().build();
    }

    /** 인증 주체는 members.id를 담는다 (LoginMember 계약) */
    private long memberId(LoginMember member) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return member.memberId();
    }
}
