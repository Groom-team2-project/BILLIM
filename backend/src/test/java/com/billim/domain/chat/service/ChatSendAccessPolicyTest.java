package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRentalReadRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.safety.service.SanctionService;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatSendAccessPolicyTest {

    private static final long ROOM_ID = 901L;
    private static final long ITEM_ID = 101L;
    private static final long MEMBER_ID = 2L;
    private static final long COMMUNITY_ID = 100L;

    private final ChatRoomRentalReadRepository rentalReadRepository =
        mock(ChatRoomRentalReadRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final CommunityMembershipQuery membershipQuery = mock(CommunityMembershipQuery.class);
    private final BlockQuery blockQuery = mock(BlockQuery.class);
    private final ChatRoomLinkedRentalService linkedRentalService =
        mock(ChatRoomLinkedRentalService.class);
    private final SanctionService sanctionService = mock(SanctionService.class);
    private final ChatSendAccessPolicy policy = new ChatSendAccessPolicy(
        rentalReadRepository, itemRepository, membershipQuery,
        linkedRentalService, sanctionService, blockQuery
    );

    @Test
    @DisplayName("제재되지 않은 참여자는 거래 ID 없이 일반 메시지를 보낼 수 있다")
    void unrestrictedMemberCanSendOrdinaryMessage() {
        givenPublicItemAndActiveCommunity();

        assertThatCode(() -> policy.requireCanSend(room(), MEMBER_ID, null))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("제재된 참여자는 거래 ID 없는 일반 메시지를 보낼 수 없다")
    void restrictedMemberCannotSendOrdinaryMessage() {
        givenPublicItemAndActiveCommunity();
        when(sanctionService.isRestricted(MEMBER_ID)).thenReturn(true);

        assertThatThrownBy(() -> policy.requireCanSend(room(), MEMBER_ID, null))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_RESTRICTED));
    }

    @Test
    @DisplayName("제재된 참여자는 연결된 진행 거래 메시지만 보낼 수 있다")
    void restrictedMemberCanSendOnlyForActiveRental() {
        givenPublicItemAndActiveCommunity();
        when(sanctionService.isRestricted(MEMBER_ID)).thenReturn(true);
        when(rentalReadRepository.existsLinked(501L, ROOM_ID, ITEM_ID, 1L, MEMBER_ID))
            .thenReturn(true);
        when(linkedRentalService.getAllowedRentalIds(ROOM_ID, MEMBER_ID))
            .thenReturn(List.of("501"));

        assertThatCode(() -> policy.requireCanSend(room(), MEMBER_ID, 501L))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("제재된 참여자는 방에 연결돼도 진행 중이지 않은 거래 메시지는 보낼 수 없다")
    void restrictedMemberCannotSendForInactiveRental() {
        givenPublicItemAndActiveCommunity();
        when(sanctionService.isRestricted(MEMBER_ID)).thenReturn(true);
        when(rentalReadRepository.existsLinked(501L, ROOM_ID, ITEM_ID, 1L, MEMBER_ID))
            .thenReturn(true);
        when(linkedRentalService.getAllowedRentalIds(ROOM_ID, MEMBER_ID))
            .thenReturn(List.of());

        assertThatThrownBy(() -> policy.requireCanSend(room(), MEMBER_ID, 501L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_RESTRICTED));
    }

    @Test
    @DisplayName("차단된 참여자는 일반 메시지를 보낼 수 없다")
    void blockedMemberCannotSendOrdinaryMessage() {
        givenPublicItemAndActiveCommunity();
        when(blockQuery.isBlockedBetween(MEMBER_ID, 1L)).thenReturn(true);

        assertThatThrownBy(() -> policy.requireCanSend(room(), MEMBER_ID, null))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BLOCKED_RELATION));
    }

    @Test
    @DisplayName("차단 후에도 연결된 진행 거래 메시지는 보낼 수 있다")
    void blockedMemberCanSendForActiveRental() {
        givenPublicItemAndActiveCommunity();
        when(blockQuery.isBlockedBetween(MEMBER_ID, 1L)).thenReturn(true);
        when(rentalReadRepository.existsLinked(501L, ROOM_ID, ITEM_ID, 1L, MEMBER_ID))
            .thenReturn(true);
        when(linkedRentalService.getAllowedRentalIds(ROOM_ID, MEMBER_ID))
            .thenReturn(List.of("501"));

        assertThatCode(() -> policy.requireCanSend(room(), MEMBER_ID, 501L))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("차단 후 종료된 거래 ID로는 메시지를 보낼 수 없다")
    void blockedMemberCannotSendForInactiveRental() {
        givenPublicItemAndActiveCommunity();
        when(blockQuery.isBlockedBetween(MEMBER_ID, 1L)).thenReturn(true);
        when(rentalReadRepository.existsLinked(501L, ROOM_ID, ITEM_ID, 1L, MEMBER_ID))
            .thenReturn(true);

        assertThatThrownBy(() -> policy.requireCanSend(room(), MEMBER_ID, 501L))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BLOCKED_RELATION));
    }

    private void givenPublicItemAndActiveCommunity() {
        Item item = Item.create(
            1L, COMMUNITY_ID, 1L, 1L, "전동드릴", null,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
            Instant.parse("2026-10-14T00:00:00Z")
        );
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(membershipQuery.findActiveCommunityId(MEMBER_ID))
            .thenReturn(Optional.of(COMMUNITY_ID));
    }

    private ChatRoom room() {
        ChatRoom room = mock(ChatRoom.class);
        when(room.isParticipant(MEMBER_ID)).thenReturn(true);
        when(room.getId()).thenReturn(ROOM_ID);
        when(room.getItemId()).thenReturn(ITEM_ID);
        when(room.getOwnerId()).thenReturn(1L);
        when(room.getRequesterId()).thenReturn(MEMBER_ID);
        return room;
    }
}
