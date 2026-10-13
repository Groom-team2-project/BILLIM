package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatAccessResponse;
import com.billim.domain.chat.dto.ChatRoomPageResponse;
import com.billim.domain.chat.dto.ChatRoomResponse;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.rental.dto.RentalSummaryResponse;
import com.billim.domain.rental.entity.Rental;
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
    private final ChatRoomAccessPolicy accessPolicy;

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
                .map(this::toRentalSummary)
                .toList();

        ChatRoomAccessPolicy.Restriction restriction =
            resolveRestriction(roomId, memberId);
        ChatAccessResponse access =
            accessPolicy.resolve(roomId, memberId, restriction);

        return ChatRoomResponse.from(base, rentals, access);
    }

    private RentalSummaryResponse toRentalSummary(Rental rental) {
        // TODO(대여): 대여 도메인의 RentalSummary 변환 기능 연결.
        throw new BusinessException(
            ErrorCode.DEPENDENCY_UNAVAILABLE,
            "대여 요약 연동 전이라 처리할 수 없습니다."
        );
    }

    private ChatRoomAccessPolicy.Restriction resolveRestriction(
        Long roomId,
        Long memberId
    ) {
        // TODO(동네·차단): 동네 변경, 차단, 물건 상태 및 제재 판정 연결.
        throw new BusinessException(
            ErrorCode.DEPENDENCY_UNAVAILABLE,
            "채팅 권한 연동 전이라 처리할 수 없습니다."
        );
    }
}
