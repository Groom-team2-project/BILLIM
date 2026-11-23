package com.billim.domain.chat.dto;

import java.util.List;

public record MessagePageResponse(
    List<MessageResponse> items,
    boolean hasMore,
    Long nextAfterSequence,
    Long nextBeforeSequence,
    long otherLastReadSequence
) {
}
