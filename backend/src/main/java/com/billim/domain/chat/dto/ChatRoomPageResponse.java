package com.billim.domain.chat.dto;

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
}
