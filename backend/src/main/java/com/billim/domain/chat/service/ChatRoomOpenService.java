package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.item.service.ItemAccessPolicy;
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
    private final ItemAccessPolicy itemAccessPolicy;
    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;

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
        if (!itemAccessPolicy.canView(item, requesterId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return roomRepository.findByItemIdAndRequesterId(itemId, requesterId)
            .orElseGet(() -> createRoom(item, requesterId));
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
