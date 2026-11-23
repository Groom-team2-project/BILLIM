package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.MessageResponse;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        String body = null;
        if (!lastest.isEmpty()) {
            ChatMessage latest = lastest.getFirst();
            body = latest.getType() == ChatMessage.Type.SYSTEM
                ? MessageResponse.from(latest).system().label()
                : latest.getBody();
        }
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
            String.valueOf(item.getCommunityId()),
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

    @Transactional(readOnly = true)
    public List<RoomBase> getForRooms(List<ChatRoom> rooms, Long memberId) {
        if (memberId == null || memberId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (rooms.isEmpty()) {
            return List.of();
        }

        List<Long> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        Map<Long, ChatParticipant> participants = participantRepository
            .findByMemberIdAndRoomIdIn(memberId, roomIds).stream()
            .collect(Collectors.toMap(ChatParticipant::getRoomId, Function.identity()));
        List<Long> itemIds = rooms.stream().map(ChatRoom::getItemId).distinct().toList();
        Map<Long, Item> items = itemRepository.findAllById(itemIds).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));
        List<Long> otherMemberIds = rooms.stream()
            .map(room -> room.getOwnerId().equals(memberId)
                ? room.getRequesterId() : room.getOwnerId())
            .distinct().toList();
        Map<Long, Member> otherMembers = memberRepository.findAllById(otherMemberIds).stream()
            .collect(Collectors.toMap(Member::getId, Function.identity()));
        Map<Long, ChatMessage> latest = messageRepository.findLatestMessagesInRooms(roomIds)
            .stream().collect(Collectors.toMap(ChatMessage::getRoomId, Function.identity()));
        Map<Long, Long> unread = messageRepository.countUnreadMessagesInRooms(
                roomIds, memberId, ChatMessage.Type.TEXT).stream()
            .collect(Collectors.toMap(ChatMessageRepository.RoomUnreadCount::getRoomId,
                ChatMessageRepository.RoomUnreadCount::getUnreadCount));

        return rooms.stream().map(room -> {
            if (!room.isParticipant(memberId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            ChatParticipant participant = participants.get(room.getId());
            if (participant == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            Item item = items.get(room.getItemId());
            Member otherMember = otherMembers.get(room.getOwnerId().equals(memberId)
                ? room.getRequesterId() : room.getOwnerId());
            if (item == null || otherMember == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            }
            ChatMessage lastMessage = latest.get(room.getId());
            String body = lastMessage == null ? null : lastMessage.getType() == ChatMessage.Type.SYSTEM
                ? MessageResponse.from(lastMessage).system().label() : lastMessage.getBody();
            String preview = body == null ? null : body.substring(0, Math.min(body.length(), 200));
            return new RoomBase(
                String.valueOf(room.getId()), String.valueOf(room.getItemId()),
                String.valueOf(item.getCommunityId()), item.getTitle(),
                new MemberSummaryResponse(String.valueOf(otherMember.getId()),
                    otherMember.getDisplayName(), otherMember.getCreatedAt()),
                Math.toIntExact(unread.getOrDefault(room.getId(), 0L)),
                preview, room.getLastMessageAt()
            );
        }).toList();
    }

    public record RoomBase(
        String id,
        String itemId,
        String communityId,
        String itemTitle,
        MemberSummaryResponse otherMember,
        int unreadCount,
        String lastMessagePreview,
        Instant lastMessageAt
    ) {

    }
}
