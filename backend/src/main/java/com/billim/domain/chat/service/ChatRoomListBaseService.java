package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatMessage;
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

        List<ChatRoomBaseService.RoomBase> items = baseService.getForRooms(rooms.getContent(), memberId);

        return new RoomBasePage(
            items,
            page,
            size,
            rooms.getTotalElements(),
            rooms.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public RoomBasePage getUnread(Long memberId, int page, int size) {
        validatePage(memberId, page, size);
        Page<ChatRoom> rooms = roomRepository.findParticipatingUnreadRooms(
            memberId,
            ChatMessage.Type.TEXT,
            PageRequest.of(page, size)
        );
        return toBasePage(rooms, memberId, page, size);
    }

    @Transactional(readOnly = true)
    public RoomBasePage getBorrower(Long memberId, int page, int size) {
        return getRentalRooms(memberId, page, size, true);
    }

    @Transactional(readOnly = true)
    public RoomBasePage getOwner(Long memberId, int page, int size) {
        return getRentalRooms(memberId, page, size, false);
    }

    @Transactional(readOnly = true)
    public RoomBasePage getFiltered(Long memberId, String filter, int page, int size) {
        return switch (filter == null ? "ALL" : filter) {
            case "ALL" -> getAll(memberId, page, size);
            case "BORROWER" -> getBorrower(memberId, page, size);
            case "OWNER" -> getOwner(memberId, page, size);
            case "UNREAD" -> getUnread(memberId, page, size);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
    }

    private RoomBasePage getRentalRooms(Long memberId, int page, int size, boolean borrower) {
        validatePage(memberId, page, size);
        Page<ChatRoom> rooms = roomRepository.findParticipatingRentalRooms(
            memberId, borrower, PageRequest.of(page, size)
        );
        return toBasePage(rooms, memberId, page, size);
    }

    private RoomBasePage toBasePage(Page<ChatRoom> rooms, Long memberId, int page, int size) {
        List<ChatRoomBaseService.RoomBase> items = baseService.getForRooms(rooms.getContent(), memberId);

        return new RoomBasePage(
            items,
            page,
            size,
            rooms.getTotalElements(),
            rooms.hasNext()
        );
    }

    private void validatePage(Long memberId, int page, int size) {
        if (memberId == null || memberId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        if (page < 0 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
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
