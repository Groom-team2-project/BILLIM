package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRentalReadRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.service.ItemAccessPolicy;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatSendAccessPolicy {

    private final ChatRoomRentalReadRepository rentalReadRepository;
    private final ItemRepository itemRepository;
    private final ItemAccessPolicy itemAccessPolicy;
    private final ChatRoomLinkedRentalService linkedRentalService;
    private final SanctionService sanctionService;

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

        Item item = itemRepository.findById(room.getItemId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (item.getVisibility() != ItemVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long activeCommunityId =
            itemAccessPolicy.findActiveCommunityId(senderId).orElse(null);

        if (!item.getCommunityId().equals(activeCommunityId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        }

        if (sanctionService.isRestricted(senderId)
            && (rentalId == null
            || !linkedRentalService.getAllowedRentalIds(room.getId(), senderId)
            .contains(rentalId.toString()))) {
            throw new BusinessException(ErrorCode.MEMBER_RESTRICTED);
        }
    }
}
