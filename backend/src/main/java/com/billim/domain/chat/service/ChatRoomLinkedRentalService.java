package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRentalReadRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomLinkedRentalService {

    private final ChatRoomRepository roomRepository;
    private final ChatRoomRentalReadRepository rentalReadRepository;

    @Transactional(readOnly = true)
    public List<Rental> get(Long roomId, Long memberId) {
        if (roomId == null || roomId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (memberId == null || memberId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        ChatRoom room = roomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!room.isParticipant(memberId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return rentalReadRepository.findLinked(
            room.getId(),
            room.getItemId(),
            room.getOwnerId(),
            room.getRequesterId()
        );
    }

    @Transactional(readOnly = true)
    public List<String> getAllowedRentalIds(Long roomId, Long memberId) {
        return allowedRentalIds(get(roomId, memberId));
    }

    public static List<String> allowedRentalIds(List<Rental> rentals) {
        return rentals.stream()
            .filter(rental ->
                rental.getStatus() == RentalStatus.APPROVED
                    || rental.getStatus() == RentalStatus.ACTIVE
            )
            .map(rental -> String.valueOf(rental.getId()))
            .toList();
    }
}
