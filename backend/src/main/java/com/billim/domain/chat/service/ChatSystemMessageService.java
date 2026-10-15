package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.SystemPayloadResponse;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.json.JsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatSystemMessageService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final RentalRepository rentalRepository;
    private final JsonCodec jsonCodec;

    @Transactional
    public ChatMessage append(
        Long roomId,
        Long rentalId,
        Long appointmentProposalId,
        Long eventId,
        SystemPayloadResponse system
    ) {
        if (roomId == null || roomId < 1
            || rentalId == null || rentalId < 1
            || eventId == null || eventId < 1
            || system == null
            || system.eventType() == null || system.eventType().isBlank()
            || system.occurredAt() == null
            || system.label() == null || system.label().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        ChatRoom room = roomRepository.findLockedById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Rental rental = rentalRepository.findById(rentalId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!Objects.equals(rental.getChatRoomId(), room.getId())
            || !Objects.equals(rental.getItemId(), room.getItemId())
            || !Objects.equals(rental.getOwnerId(), room.getOwnerId())
            || !Objects.equals(rental.getBorrowerId(), room.getRequesterId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        ChatMessage existing = messageRepository
            .findByRoomIdAndEventId(roomId, eventId)
            .orElse(null);
        if (existing != null) {
            return existing;
        }

        Instant now = Instant.now();
        ChatMessage message = ChatMessage.system(
            roomId,
            room.allocateSequence(now),
            rentalId,
            appointmentProposalId,
            eventId,
            jsonCodec.write(system),
            now
        );
        return messageRepository.saveAndFlush(message);
    }
}
