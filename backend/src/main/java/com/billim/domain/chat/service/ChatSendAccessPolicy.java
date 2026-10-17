package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRentalReadRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.safety.service.BlockQuery;
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
    private final CommunityMembershipQuery membershipQuery;
    private final ChatRoomLinkedRentalService linkedRentalService;
    private final SanctionService sanctionService;
    private final BlockQuery blockQuery;

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
            membershipQuery.findActiveCommunityId(senderId).orElse(null);
        if (!item.getCommunityId().equals(activeCommunityId)) {
            throw new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        }

        Long otherMemberId = room.getOwnerId().equals(senderId)
            ? room.getRequesterId()
            : room.getOwnerId();
        boolean restricted = sanctionService.isRestricted(senderId);
        boolean blocked = blockQuery.isBlockedBetween(senderId, otherMemberId);

        if (restricted || blocked) {
            boolean activeLinkedRental = rentalId != null
                && linkedRentalService.getAllowedRentalIds(room.getId(), senderId)
                .contains(rentalId.toString());
            if (!activeLinkedRental) {
                throw new BusinessException(
                    restricted ? ErrorCode.MEMBER_RESTRICTED : ErrorCode.BLOCKED_RELATION
                );
            }
        }
    }
}
