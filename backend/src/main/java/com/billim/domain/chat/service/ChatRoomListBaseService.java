package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomListBaseService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomBaseService baseService;

    @Transactional(readOnly = true)
    public RoomBasePage getAll(Long memberId, int page, int size) {
        if (memberId == null || memberId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Page<ChatRoom> rooms = roomRepository.findParticipatingRooms(
            memberId, PageRequest.of(page, size)
        );

        List<ChatRoomBaseService.RoomBase> items = rooms.getContent().stream()
            .map(room -> baseService.get(room.getId(), memberId))
            .toList();

        return new RoomBasePage(
            items,
            page,
            size,
            rooms.getTotalElements(),
            rooms.hasNext()
        );
    }

    public record RoomBasePage(
        List<ChatRoomBaseService.RoomBase> items,
        int page,
        int size,
        long totalElements,
        boolean hasNext
    ) {
    }
}
