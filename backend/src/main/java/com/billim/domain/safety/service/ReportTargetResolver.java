package com.billim.domain.safety.service;

import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.safety.entity.ReportTargetType;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * targetId를 대상 회원·커뮤니티·증거로 해석하고, 신고자가 그 대상을 볼 수 있는지 확인한다.
 * (API 명세 12.4 — "신고 대상 접근 권한 필요")
 *
 * <p>볼 수 없는 대상은 존재를 알리지 않고 404로 돌려보낸다 (명세 0.2).
 *
 * <p>소속 판정은 {@code members.active_community_id}를 쓴다. 인증 만료까지 보는 유효 소속 판정은
 * community_memberships가 생긴 뒤 교체한다. TODO(E 커뮤니티).
 */
@Component
public class ReportTargetResolver {

    private final MemberRepository memberRepository;
    private final ItemRepository itemRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    public ReportTargetResolver(MemberRepository memberRepository,
                                ItemRepository itemRepository,
                                ChatMessageRepository chatMessageRepository,
                                ChatRoomRepository chatRoomRepository,
                                ChatParticipantRepository chatParticipantRepository) {
        this.memberRepository = memberRepository;
        this.itemRepository = itemRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatParticipantRepository = chatParticipantRepository;
    }

    public ReportTarget resolve(Member reporter, ReportTargetType targetType, long targetId) {
        return switch (targetType) {
            case MEMBER -> resolveMember(reporter, targetId);
            case ITEM -> resolveItem(reporter, targetId);
            case MESSAGE -> resolveMessage(reporter, targetId);
        };
    }

    /** 회원 신고. 같은 동네 이웃만 신고할 수 있고 맥락은 신고자의 동네다 */
    private ReportTarget resolveMember(Member reporter, long targetMemberId) {
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireNotSelf(reporter, target.getId());

        return new ReportTarget(
                target.getId(),
                null,
                requireActiveCommunity(reporter),
                ReportTarget.ReportSnapshot.of(target.getDisplayName(), null));
    }

    /** 물건 신고. 대상 회원은 소유자고 맥락은 물건이 등록된 동네다 */
    private ReportTarget resolveItem(Member reporter, long itemId) {
        Item item = itemRepository.findById(itemId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        requireNotSelf(reporter, item.getOwnerId());

        // 다른 동네 물건은 애초에 볼 수 없으므로 신고도 할 수 없다
        if (!item.getCommunityId().equals(reporter.getActiveCommunityId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return new ReportTarget(
                item.getOwnerId(),
                item.getId(),
                item.getCommunityId(),
                ReportTarget.ReportSnapshot.of(item.getTitle(), item.getDescription()));
    }

    /** 메시지 신고. 그 방의 참여자만 신고할 수 있고 맥락은 문의한 물건의 동네다 */
    private ReportTarget resolveMessage(Member reporter, long messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 참여하지 않은 방의 메시지는 존재를 알리지 않는다
        chatParticipantRepository.findByRoomIdAndMemberId(message.getRoomId(), reporter.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        // 시스템 메시지는 보낸 회원이 없어 신고 대상이 될 수 없다
        if (message.getSenderId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "신고할 수 없는 메시지입니다.");
        }
        requireNotSelf(reporter, message.getSenderId());

        ChatRoom room = chatRoomRepository.findById(message.getRoomId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Item item = itemRepository.findById(room.getItemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return new ReportTarget(
                message.getSenderId(),
                message.getId(),
                item.getCommunityId(),
                ReportTarget.ReportSnapshot.of(item.getTitle(), message.getBody()));
    }

    private void requireNotSelf(Member reporter, long targetMemberId) {
        if (reporter.getId() == targetMemberId) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "자기 자신은 신고할 수 없습니다.");
        }
    }

    private long requireActiveCommunity(Member reporter) {
        Long communityId = reporter.getActiveCommunityId();
        if (communityId == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        }
        return communityId;
    }
}
