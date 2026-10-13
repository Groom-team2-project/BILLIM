package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRentalReadRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSendAccessPolicy {

    private final ChatRoomRentalReadRepository rentalReadRepository;

    public void requireCanSend(
        ChatRoom room,
        Long senderId,
        Long rentalId
    ) {
        if (!room.isParticipant(senderId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (rentalId != null && !rentalReadRepository.existsLinked(
            rentalId,
            room.getId(),
            room.getItemId(),
            room.getOwnerId(),
            room.getRequesterId()
        )) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
