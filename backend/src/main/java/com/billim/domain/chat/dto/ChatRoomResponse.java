package com.billim.domain.chat.dto;

import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.rental.dto.RentalSummaryResponse;

import java.time.Instant;
import java.util.List;

public record ChatRoomResponse(
    String id,
    String itemId,
    String itemTitle,
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
}
