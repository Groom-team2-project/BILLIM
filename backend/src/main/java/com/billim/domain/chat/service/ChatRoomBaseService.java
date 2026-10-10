package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.member.dto.MemberSummaryResponse;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomBaseService {

    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public RoomBase get(Long roomId, Long memberId) {
        if (roomId == null || roomId < 1 || memberId == null || memberId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        ChatRoom room = roomRepository.findById(roomId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (!room.isParticipant(memberId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        ChatParticipant participant = participantRepository
            .findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Long otherMemberId = room.getOwnerId().equals(memberId)
            ? room.getRequesterId()
            : room.getOwnerId();

        Member otherMember = memberRepository.findById(otherMemberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Item item = itemRepository.findById(room.getItemId())
            .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<ChatMessage> lastest = messageRepository
            .findByRoomIdOrderBySequenceDesc(roomId, PageRequest.of(0, 1));

        String body = lastest.isEmpty() ? null : lastest.getFirst().getBody();
        String preview = body == null
            ? null
            : body.substring(0, Math.min(body.length(), 200));

        long unread = messageRepository.countUnreadMessagesInRoom(
            roomId,
            participant.getLastReadSequence(),
            memberId,
            ChatMessage.Type.TEXT
        );

        return new RoomBase(
            String.valueOf(room.getId()),
            String.valueOf(room.getItemId()),
            item.getTitle(),
            new MemberSummaryResponse(
                String.valueOf(otherMember.getId()),
                otherMember.getDisplayName(),
                otherMember.getCreatedAt()
            ),
            Math.toIntExact(unread),
            preview,
            room.getLastMessageAt()
        );
    }

    public record RoomBase(
        String id,
        String itemId,
        String itemTitle,
        MemberSummaryResponse otherMember,
        int unreadCount,
        String lastMessagePreview,
        Instant lastMessageAt
    ) {

    }
}
