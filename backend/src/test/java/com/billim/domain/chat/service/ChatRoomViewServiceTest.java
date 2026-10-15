package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.safety.service.SanctionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRoomViewServiceTest {

    private static final long ROOM_ID = 901L;
    private static final long MEMBER_ID = 2L;
    private static final long ITEM_ID = 101L;
    private static final long COMMUNITY_ID = 100L;

    private final ChatRoomOpenService openService = mock(ChatRoomOpenService.class);
    private final ChatRoomBaseService baseService = mock(ChatRoomBaseService.class);
    private final ChatRoomListBaseService listBaseService = mock(ChatRoomListBaseService.class);
    private final ChatRoomLinkedRentalService linkedRentalService = mock(ChatRoomLinkedRentalService.class);
    private final ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
    private final ItemRepository itemRepository = mock(ItemRepository.class);
    private final CommunityMembershipQuery membershipQuery = mock(CommunityMembershipQuery.class);
    private final BlockQuery blockQuery = mock(BlockQuery.class);
    private final SanctionService sanctionService = mock(SanctionService.class);
    private final ChatRoomViewService viewService = new ChatRoomViewService(
        openService, baseService, listBaseService, linkedRentalService,
        new ChatRoomAccessPolicy(linkedRentalService),
        roomRepository, itemRepository, membershipQuery, blockQuery, sanctionService
    );

    @Test
    @DisplayName("활성 동네가 바뀐 참여자는 기존 방을 조회해도 메시지를 보낼 수 없다")
    void changedCommunityCannotSend() {
        givenRoom();
        when(membershipQuery.findActiveCommunityId(MEMBER_ID)).thenReturn(Optional.of(999L));
        when(linkedRentalService.getAllowedRentalIds(ROOM_ID, MEMBER_ID)).thenReturn(List.of());

        var response = viewService.get(ROOM_ID, MEMBER_ID);

        assertThat(response.access().canSend()).isFalse();
        assertThat(response.access().allowedRentalIds()).isEmpty();
        assertThat(response.access().reason()).isEqualTo("COMMUNITY_CHANGED");
    }

    @Test
    @DisplayName("활성 동네가 없어도 채팅 전송을 허용하지 않는다")
    void missingCommunityCannotSend() {
        givenRoom();
        when(membershipQuery.findActiveCommunityId(MEMBER_ID)).thenReturn(Optional.empty());
        when(linkedRentalService.getAllowedRentalIds(ROOM_ID, MEMBER_ID)).thenReturn(List.of());

        var response = viewService.get(ROOM_ID, MEMBER_ID);

        assertThat(response.access().canSend()).isFalse();
        assertThat(response.access().reason()).isEqualTo("COMMUNITY_CHANGED");
    }

    @Test
    @DisplayName("유효한 동네 소속이고 차단이 없으면 일반 메시지를 보낼 수 있다")
    void validCommunityWithoutBlockCanSend() {
        givenRoom();
        when(membershipQuery.findActiveCommunityId(MEMBER_ID))
            .thenReturn(Optional.of(COMMUNITY_ID));

        var response = viewService.get(ROOM_ID, MEMBER_ID);

        assertThat(response.access().canSend()).isTrue();
        assertThat(response.access().reason()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("양방향 중 한쪽이라도 차단하면 일반 메시지 전송을 막는다")
    void blockedMembersCannotSendOrdinaryMessage() {
        givenRoom();
        when(membershipQuery.findActiveCommunityId(MEMBER_ID))
            .thenReturn(Optional.of(COMMUNITY_ID));
        when(blockQuery.isBlockedBetween(MEMBER_ID, 1L)).thenReturn(true);

        var response = viewService.get(ROOM_ID, MEMBER_ID);

        assertThat(response.access().canSend()).isFalse();
        assertThat(response.access().reason()).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("비공개 물건의 채팅방은 메시지 전송을 허용하지 않는다")
    void hiddenItemCannotSend() {
        givenRoom();
        Item item = item();
        item.changeVisibility(ItemVisibility.HIDDEN, Instant.parse("2026-10-14T00:00:00Z"));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(membershipQuery.findActiveCommunityId(MEMBER_ID))
            .thenReturn(Optional.of(COMMUNITY_ID));

        var response = viewService.get(ROOM_ID, MEMBER_ID);

        assertThat(response.access().canSend()).isFalse();
        assertThat(response.access().reason()).isEqualTo("ITEM_UNAVAILABLE");
    }

    @Test
    @DisplayName("제재된 참여자는 진행 거래가 없으면 메시지를 보낼 수 없다")
    void restrictedMemberCannotSendWithoutRental() {
        givenRoom();
        when(membershipQuery.findActiveCommunityId(MEMBER_ID))
            .thenReturn(Optional.of(COMMUNITY_ID));
        when(sanctionService.isRestricted(MEMBER_ID)).thenReturn(true);

        var response = viewService.get(ROOM_ID, MEMBER_ID);

        assertThat(response.access().canSend()).isFalse();
        assertThat(response.access().reason()).isEqualTo("RESTRICTED");
    }

    private void givenRoom() {
        when(baseService.get(ROOM_ID, MEMBER_ID)).thenReturn(
            new ChatRoomBaseService.RoomBase(
                String.valueOf(ROOM_ID), String.valueOf(ITEM_ID), "전동드릴",
                null, 0, null, null
            )
        );
        when(linkedRentalService.get(ROOM_ID, MEMBER_ID)).thenReturn(List.of());
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(
            ChatRoom.open(ITEM_ID, 1L, MEMBER_ID, Instant.parse("2026-10-14T00:00:00Z"))
        ));
        when(itemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item()));
    }

    private Item item() {
        return Item.create(
            1L, COMMUNITY_ID, 1L, 1L, "전동드릴", null,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
            Instant.parse("2026-10-14T00:00:00Z")
        );
    }
}
