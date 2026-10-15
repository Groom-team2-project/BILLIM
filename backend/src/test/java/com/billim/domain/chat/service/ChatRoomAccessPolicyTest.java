package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatAccessResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.billim.domain.chat.service.ChatRoomAccessPolicy.Restriction.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRoomAccessPolicyTest {

    private final ChatRoomLinkedRentalService linkedRentalService =
        mock(ChatRoomLinkedRentalService.class);
    private final ChatRoomAccessPolicy policy = new ChatRoomAccessPolicy(linkedRentalService);

    @Test
    @DisplayName("제한이 없으면 일반 메시지 전송을 허용한다")
    void allowsNormalMessagesWithoutRestriction() {
        when(linkedRentalService.getAllowedRentalIds(1L, 2L)).thenReturn(List.of());

        ChatAccessResponse access = policy.resolve(1L, 2L, NONE);

        assertThat(access.canSend()).isTrue();
        assertThat(access.allowedRentalIds()).isEmpty();
        assertThat(access.reason()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("차단 또는 제재 중 진행 거래가 있으면 해당 거래 메시지만 허용한다")
    void allowsOnlyExistingRentalWhenBlockedOrRestricted() {
        when(linkedRentalService.getAllowedRentalIds(1L, 2L)).thenReturn(List.of("10"));

        for (ChatRoomAccessPolicy.Restriction restriction : List.of(BLOCKED, RESTRICTED)) {
            ChatAccessResponse access = policy.resolve(1L, 2L, restriction);
            assertThat(access.canSend()).isTrue();
            assertThat(access.allowedRentalIds()).containsExactly("10");
            assertThat(access.reason()).isEqualTo("EXISTING_RENTAL_ONLY");
        }
    }

    @Test
    @DisplayName("차단 또는 제재 중 진행 거래가 없으면 전송을 거부한다")
    void rejectsBlockedOrRestrictedWithoutExistingRental() {
        when(linkedRentalService.getAllowedRentalIds(1L, 2L)).thenReturn(List.of());

        for (ChatRoomAccessPolicy.Restriction restriction : List.of(BLOCKED, RESTRICTED)) {
            ChatAccessResponse access = policy.resolve(1L, 2L, restriction);
            assertThat(access.canSend()).isFalse();
            assertThat(access.allowedRentalIds()).isEmpty();
            assertThat(access.reason()).isEqualTo(restriction.name());
        }
    }

    @Test
    @DisplayName("동네 변경 또는 물건 이용 불가 상태에서는 전송을 거부한다")
    void rejectsCommunityOrItemRestriction() {
        when(linkedRentalService.getAllowedRentalIds(1L, 2L)).thenReturn(List.of("10"));

        for (ChatRoomAccessPolicy.Restriction restriction :
            List.of(COMMUNITY_CHANGED, ITEM_UNAVAILABLE)) {
            ChatAccessResponse access = policy.resolve(1L, 2L, restriction);
            assertThat(access.canSend()).isFalse();
            assertThat(access.allowedRentalIds()).isEmpty();
            assertThat(access.reason()).isEqualTo(restriction.name());
        }
    }
}
