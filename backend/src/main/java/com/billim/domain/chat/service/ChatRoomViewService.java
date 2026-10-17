package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatAccessResponse;
import com.billim.domain.chat.dto.ChatRoomPageResponse;
import com.billim.domain.chat.dto.ChatRoomResponse;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.rental.dto.RentalSummaryResponse;
import com.billim.domain.rental.service.RentalQueryService;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomViewService {

    private final ChatRoomOpenService openService;
    private final ChatRoomBaseService baseService;
    private final ChatRoomListBaseService listBaseService;
    private final ChatRoomLinkedRentalService linkedRentalService;
    private final RentalQueryService rentalQueryService;
    private final ChatRoomAccessPolicy accessPolicy;
    private final ChatRoomRepository roomRepository;
    private final ItemRepository itemRepository;
    private final CommunityMembershipQuery membershipQuery;
    private final BlockQuery blockQuery;
    private final SanctionService sanctionService;

    @Transactional
    public ChatRoomResponse open(Long itemId, Long memberId) {
        ChatRoom room = openService.open(itemId, memberId);
        return get(room.getId(), memberId);
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse get(Long roomId, Long memberId) {
        return toResponse(baseService.get(roomId, memberId), memberId);
    }

    @Transactional(readOnly = true)
    public ChatRoomPageResponse list(Long memberId, String filter, int page, int size) {
        ChatRoomListBaseService.RoomBasePage basePage =
            listBaseService.getFiltered(memberId, filter, page, size);

        List<ChatRoomResponse> items = basePage.items().stream()
            .map(base -> toResponse(base, memberId))
            .toList();

        return ChatRoomPageResponse.from(basePage, items);
    }

    private ChatRoomResponse toResponse(
        ChatRoomBaseService.RoomBase base,
        Long memberId
    ) {
        Long roomId = Long.valueOf(base.id());

        List<RentalSummaryResponse> rentals =
            linkedRentalService.get(roomId, memberId).stream()
                .map(rental -> rentalQueryService.getRentalSummary(rental.getId(), memberId))
                .toList();

        ChatRoomAccessPolicy.Restriction restriction =
            resolveRestriction(roomId, memberId);
        ChatAccessResponse access =
            accessPolicy.resolve(roomId, memberId, restriction);

        return ChatRoomResponse.from(base, rentals, access);
    }

    private ChatRoomAccessPolicy.Restriction resolveRestriction(
        Long roomId,
        Long memberId
    ) {
        ChatRoom room = roomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Item item = itemRepository.findById(room.getItemId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Long activeCommunityId =
            membershipQuery.findActiveCommunityId(memberId).orElse(null);
        if (!item.getCommunityId().equals(activeCommunityId)) {
            return ChatRoomAccessPolicy.Restriction.COMMUNITY_CHANGED;
        }

        if (item.getVisibility() != ItemVisibility.PUBLIC) {
            return ChatRoomAccessPolicy.Restriction.ITEM_UNAVAILABLE;
        }

        if (sanctionService.isRestricted(memberId)) {
            return ChatRoomAccessPolicy.Restriction.RESTRICTED;
        }

        Long otherMemberId = room.getOwnerId().equals(memberId)
            ? room.getRequesterId()
            : room.getOwnerId();
        if (blockQuery.isBlockedBetween(memberId, otherMemberId)) {
            return ChatRoomAccessPolicy.Restriction.BLOCKED;
        }

        return ChatRoomAccessPolicy.Restriction.NONE;
    }
}
