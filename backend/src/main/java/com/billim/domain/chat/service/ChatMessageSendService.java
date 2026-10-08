package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.SendMessageRequest;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSendAccessPolicy accessPolicy;

    @Transactional
    public ChatMessage send(
        Long roomId,
        Long senderId,
        SendMessageRequest request
    ) {
        validateRequest(roomId, senderId, request);

        Long rentalId = parseRentalId(request.rentalId());

        ChatRoom room = roomRepository.findLockedById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        accessPolicy.requireCanSend(room, senderId, rentalId);

        ChatMessage existing = messageRepository
            .findByRoomIdAndSenderIdAndClientMessageId(
                roomId,
                senderId,
                request.clientMessageId().toString()
            )
            .orElse(null);

        if (existing != null) {
            if (!sameContent(existing, request, rentalId)) {
                throw new BusinessException(ErrorCode.MESSAGE_KEY_REUSED);
            }

            return existing;
        }

        Instant now = Instant.now();

        ChatMessage message = ChatMessage.text(
            roomId,
            room.allocateSequence(now),
            senderId,
            request.body(),
            rentalId,
            request.clientMessageId(),
            now
        );

        return messageRepository.saveAndFlush(message);
    }

    private boolean sameContent(ChatMessage existing, SendMessageRequest request, Long rentalId) {
        return existing.getBody().equals(request.body())
            && Objects.equals(existing.getRentalId(), rentalId);
    }

    private Long parseRentalId(String value) {
        if (value == null) {
            return null;
        }

        try {
            long rentalId = Long.parseLong(value);

            if (rentalId < 1) {
                throw new NumberFormatException();
            }

            return rentalId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateRequest(Long roomId, Long senderId, SendMessageRequest request) {
        if (roomId == null || roomId < 1
            || senderId == null || senderId < 1
            || request == null
            || request.clientMessageId() == null
            || request.body() == null
            || request.body().isBlank()
            || request.body().length() > 2000) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

}
