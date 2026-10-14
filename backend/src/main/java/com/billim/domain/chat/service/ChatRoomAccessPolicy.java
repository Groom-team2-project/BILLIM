package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatAccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomAccessPolicy {

    private final ChatRoomLinkedRentalService linkedRentalService;

    @Transactional(readOnly = true)
    public ChatAccessResponse resolve(
        Long roomId,
        Long memberId,
        Restriction restriction
    ) {
        List<String> allowedRentalIds =
            linkedRentalService.getAllowedRentalIds(roomId, memberId);

        return switch (restriction) {
            case NONE -> new ChatAccessResponse(
                true, allowedRentalIds, "NORMAL"
            );
            case BLOCKED, RESTRICTED -> allowedRentalIds.isEmpty()
                ? new ChatAccessResponse(false, List.of(), restriction.name())
                : new ChatAccessResponse(true, allowedRentalIds, "EXISTING_RENTAL_ONLY");
            case COMMUNITY_CHANGED, ITEM_UNAVAILABLE -> new ChatAccessResponse(false, List.of(), restriction.name());
        };
    }

    public enum Restriction {
        NONE,
        BLOCKED,
        RESTRICTED,
        COMMUNITY_CHANGED,
        ITEM_UNAVAILABLE
    }
}
