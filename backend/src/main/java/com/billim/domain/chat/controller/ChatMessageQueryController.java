package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.MessagePageResponse;
import com.billim.domain.chat.service.ChatMessageQueryService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
public class ChatMessageQueryController {

    private final ChatMessageQueryService queryService;

    @GetMapping("/{roomId}/messages")
    public MessagePageResponse getMessage(
        @PathVariable Long roomId,
        @RequestParam(required = false) Long afterSequence,
        @RequestParam(required = false) Long beforeSequence,
        @RequestParam(defaultValue = "50") int limit,
        @AuthenticationPrincipal LoginMember member
        ) {
            if (member == null || member.memberId() == null) {
                throw new BusinessException(ErrorCode.UNAUTHENTICATED);
            }

            return queryService.getMessages(
                roomId,
                member.memberId(),
                afterSequence,
                beforeSequence,
                limit
            );
    }
}
