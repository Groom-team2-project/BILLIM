package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.MessagePageResponse;
import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageQueryService {

    private static final int MAX_LIMIT = 50;

    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;

    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(
        Long roomId,
        Long memberId,
        Long afterSequence,
        Long beforeSequence,
        int limit
    ) {
        if (roomId == null || roomId < 1
            || memberId == null || memberId < 1
            || limit < 1 || limit > MAX_LIMIT
            || afterSequence != null && afterSequence < 0
            || beforeSequence != null && beforeSequence < 0
            || afterSequence != null && beforeSequence != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        participantRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        ChatParticipant other = participantRepository
            .findByRoomIdAndMemberIdNot(roomId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        PageRequest page = PageRequest.of(0, limit + 1);

        List<ChatMessage> fetched;
        if (afterSequence != null) {
            fetched = messageRepository
                .findByRoomIdAndSequenceGreaterThanOrderBySequenceAsc(
                    roomId, afterSequence, page
                );
        } else if (beforeSequence != null) {
            fetched = messageRepository
                .findByRoomIdAndSequenceLessThanOrderBySequenceDesc(
                    roomId, beforeSequence, page
                );
        } else {
            fetched = messageRepository
                .findByRoomIdOrderBySequenceDesc(roomId, page);
        }

        boolean hasMore = fetched.size() > limit;
        List<ChatMessage> currentPage = hasMore
            ? fetched.subList(0, limit)
            : fetched;

        List<MessageResponse> items = currentPage.stream()
            .map(this::toResponse)
            .toList();

        Long nextAfterSequence = null;
        Long nextBeforeSequence = null;

        if (hasMore) {
            long lastSequence = currentPage.getLast().getSequence();
            if (afterSequence != null) {
                nextAfterSequence = lastSequence;
            } else {
                nextBeforeSequence = lastSequence;
            }
        }

        return new MessagePageResponse(
            items,
            hasMore,
            nextAfterSequence,
            nextBeforeSequence,
            other.getLastReadSequence()
        );
    }

    private MessageResponse toResponse(ChatMessage chatMessage) {
        return new MessageResponse(
            String.valueOf(chatMessage.getId()),
            String.valueOf(chatMessage.getRoomId()),
            chatMessage.getSequence(),
            chatMessage.getType().name(),
            nullableId(chatMessage.getSenderId()),
            chatMessage.getBody(),
            nullableId(chatMessage.getRentalId()),
            null,
            chatMessage.getClientMessageId() == null
                ? null
                : UUID.fromString(chatMessage.getClientMessageId()),
            null,
            chatMessage.getCreatedAt()
        );
    }

    private String nullableId(Long id) {
        return id == null ? null : String.valueOf(id);
    }
}
