package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class ChatSendAccessPolicy {

    public void requireCanSend(
        ChatRoom room,
        Long senderId,
        Long rentalId
    ) {
        if (!room.isParticipant(senderId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // TODO:
        // rental 도메인 구현 이후 수정해야함.
        // 수정 전까지는 임의의 rentalId 저장은 차단함.
        // 이후 차단 & 제재 & 거래 검증 연결.

        if (rentalId != null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
