package com.billim.domain.chat.dto;

import com.billim.domain.chat.service.ChatRoomBaseService;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.rental.dto.RentalSummaryResponse;

import java.time.Instant;
import java.util.List;

public record ChatRoomResponse(
    String id,
    String itemId,
    String communityId,
    String itemTitle,
    String itemThumbnailUrl,
    MemberSummaryResponse otherMember,
    int unreadCount,
    String lastMessagePreview,
    Instant lastMessageAt,
    List<RentalSummaryResponse> rentals,
    ChatAccessResponse access
) {
    public ChatRoomResponse {
        rentals = List.copyOf(rentals);
    }

    public static ChatRoomResponse from(
        ChatRoomBaseService.RoomBase base,
        List<RentalSummaryResponse> rentals,
        ChatAccessResponse access,
        String itemThumbnailUrl
    ) {
        return new ChatRoomResponse(
            base.id(),
            base.itemId(),
            base.communityId(),
            base.itemTitle(),
            itemThumbnailUrl,
            base.otherMember(),
            base.unreadCount(),
            base.lastMessagePreview(),
            base.lastMessageAt(),
            rentals,
            access
        );
    }
}
