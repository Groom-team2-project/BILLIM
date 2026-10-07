package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.ReadPositionRequest;
import com.billim.domain.chat.dto.ReadPositionResponse;
import com.billim.domain.chat.dto.UnreadCountResponse;
import com.billim.domain.chat.service.ChatReadService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatReadController {

    private final ChatReadService chatReadService;

    @PutMapping("/rooms/{roomId}/read")
    public ReadPositionResponse updateReadPosition(
        @PathVariable Long roomId,
        @Valid @RequestBody ReadPositionRequest request,
        @AuthenticationPrincipal LoginMember member
    ) {
        if (roomId == null || roomId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return chatReadService.updateReadPosition(
            roomId,
            requireMemberId(member),
            request
        );
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(
        @AuthenticationPrincipal LoginMember member
    ) {
        return chatReadService.getUnreadCount(requireMemberId(member));
    }

    private Long requireMemberId(LoginMember member) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return member.memberId();
    }
}
