package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.dto.SendMessageRequest;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.service.ChatMessageSendService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
public class ChatMessageSendController {
    private final ChatMessageSendService sendService;

    @PostMapping("/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
        @PathVariable Long roomId,
        @Valid @RequestBody SendMessageRequest request,
        @AuthenticationPrincipal LoginMember member
    ) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        ChatMessage message = sendService.send(
            roomId,
            member.memberId(),
            request
        );

        return MessageResponse.from(message);
    }
}
