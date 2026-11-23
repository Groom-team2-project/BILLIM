package com.billim.domain.chat.dto;

import com.billim.domain.chat.service.ChatRoomListBaseService;

import java.util.List;

public record ChatRoomPageResponse(
    List<ChatRoomResponse> items,
    int page,
    int size,
    long totalElements,
    boolean hasNext
) {
    public ChatRoomPageResponse {
        items = List.copyOf(items);
    }

    public static ChatRoomPageResponse from(
        ChatRoomListBaseService.RoomBasePage base,
        List<ChatRoomResponse> items
    ) {
        return new ChatRoomPageResponse(
            items,
            base.page(),
            base.size(),
            base.totalElements(),
            base.hasNext()
        );
    }
}
