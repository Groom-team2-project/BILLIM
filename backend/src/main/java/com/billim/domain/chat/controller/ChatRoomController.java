package com.billim.domain.chat.controller;

import com.billim.domain.chat.dto.ChatRoomPageResponse;
import com.billim.domain.chat.dto.ChatRoomResponse;
import com.billim.domain.chat.dto.CreateRoomRequest;
import com.billim.domain.chat.service.ChatRoomViewService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@ConditionalOnProperty(name = "billim.chat.rooms.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomViewService viewService;

    @PostMapping
    public ChatRoomResponse open(
        @Valid @RequestBody CreateRoomRequest request,
        @AuthenticationPrincipal LoginMember member
    ) {
        return viewService.open(parseItemId(request.itemId()), requireMemberId(member));
    }

    @GetMapping
    public ChatRoomPageResponse list(
        @RequestParam(defaultValue = "ALL") String filter,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal LoginMember member
    ) {
        return viewService.list(requireMemberId(member), filter, page, size);
    }

    @GetMapping("/{roomId}")
    public ChatRoomResponse detail(
        @PathVariable Long roomId,
        @AuthenticationPrincipal LoginMember member
    ) {
        return viewService.get(roomId, requireMemberId(member));
    }

    private Long requireMemberId(LoginMember member) {
        if (member == null || member.memberId() == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        return member.memberId();
    }

    private Long parseItemId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // 잘못된 ID는 동일한 요청 오류로 처리.
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
}
