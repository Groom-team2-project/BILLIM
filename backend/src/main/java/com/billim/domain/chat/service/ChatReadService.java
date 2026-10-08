package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ReadPositionRequest;
import com.billim.domain.chat.dto.ReadPositionResponse;
import com.billim.domain.chat.dto.UnreadCountResponse;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChatReadService {

    private final ChatParticipantRepository participantRepository;
    private final ChatRoomRepository roomRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ReadPositionResponse updateReadPosition(
        Long roomId,
        Long memberId,
        ReadPositionRequest request
    ) {
        if (request == null || request.lastReadSequence() == null || request.lastReadSequence() < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        ChatParticipant participant = participantRepository
            .findLockedByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        ChatRoom room = roomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        long requestedSequence = request.lastReadSequence();
        long lastestSequence = room.getNextSequence() - 1;

        if (requestedSequence > lastestSequence) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        participant.advanceReadSequence(
            requestedSequence,
            lastestSequence,
            Instant.now()
        );

        return new ReadPositionResponse(participant.getLastReadSequence());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long memberId) {
        if (memberId == null || memberId < 1) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }

        long count = chatMessageRepository.countUnreadMessages(
            memberId,
            ChatMessage.Type.TEXT
        );

        return new UnreadCountResponse(Math.toIntExact(count));
    }
}
