package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.ChatSocketTicketResponse;
import com.billim.domain.chat.service.ChatSocketTicketService;
import com.billim.domain.member.entity.AuthSession;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import com.billim.global.security.session.CurrentAuthSession;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatSocketTicketController {
    private final ChatSocketTicketService ticketService;
    private final CurrentAuthSession currentAuthSession;

    @PostMapping("/api/v1/chat/rooms/{roomId}/socket-tickets")
    public ResponseEntity<ChatSocketTicketResponse> issue(
        @PathVariable Long roomId, @AuthenticationPrincipal LoginMember member,
        HttpServletRequest request
    ) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        AuthSession session = currentAuthSession.get(request)
            .filter(AuthSession::isLoggedIn)
            .filter(value -> value.getMember().getId().equals(member.memberId()))
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ticketService.issue(roomId, member.memberId(), session.getId()));
    }
}
