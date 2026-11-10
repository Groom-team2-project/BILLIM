package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomOpenService {

    private final ItemRepository itemRepository;
    private final CommunityMembershipQuery membershipQuery;
    private final BlockQuery blockQuery;
    private final SanctionService sanctionService;
    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final RentalRepository rentalRepository;
    private final ChatRoomLinkedRentalService linkedRentalService;

    @Transactional
    public ChatRoom open(Long itemId, Long requesterId) {
        if (itemId == null || itemId < 1
            || requesterId == null || requesterId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Item item = itemRepository.findByIdForUpdate(itemId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (item.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (item.isDeleted() || item.getVisibility() != ItemVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!membershipQuery.hasValidMembership(requesterId, item.getCommunityId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (blockQuery.isBlockedBetween(requesterId, item.getOwnerId())) {
            throw new BusinessException(ErrorCode.BLOCKED_RELATION);
        }
        boolean restricted = sanctionService.isRestricted(requesterId)
            || sanctionService.isRestricted(item.getOwnerId());
        var existingRoom = roomRepository.findByItemIdAndRequesterId(itemId, requesterId);
        List<Rental> allowedRentals = List.of();
        if (restricted) {
            allowedRentals = linkedRentalService.getAllowedForOpening(
                itemId, item.getOwnerId(), requesterId,
                existingRoom.map(ChatRoom::getId).orElse(null));
            if (allowedRentals.isEmpty()) {
                throw new BusinessException(ErrorCode.MEMBER_RESTRICTED);
            }
        }

        ChatRoom room = existingRoom
            .orElseGet(() -> createRoom(item, requesterId));

        if (restricted) {
            for (Rental rental : allowedRentals) {
                rental.linkChatRoom(room.getId());
            }
        } else {
            rentalRepository.linkChatRoom(
                itemId, item.getOwnerId(), requesterId, room.getId()
            );
        }

        return room;
    }

    private ChatRoom createRoom(Item item, Long requesterId) {
        Instant now = Instant.now();
        ChatRoom room = roomRepository.saveAndFlush(
            ChatRoom.open(item.getId(), item.getOwnerId(), requesterId, now)
        );

        participantRepository.saveAll(List.of(
            ChatParticipant.join(room.getId(), item.getOwnerId(), now),
            ChatParticipant.join(room.getId(), requesterId, now)
        ));

        return room;
    }
}
