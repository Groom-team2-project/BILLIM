package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatAccessResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.billim.domain.chat.service.ChatRoomAccessPolicy.Restriction.*;
import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomAccessPolicyTest {

    private final ChatRoomAccessPolicy policy = new ChatRoomAccessPolicy();

    @Test
    @DisplayName("제한이 없으면 일반 메시지 전송을 허용한다")
    void allowsNormalMessagesWithoutRestriction() {
        ChatAccessResponse access = policy.resolve(1L, 2L, NONE, List.of());

        assertThat(access.canSend()).isTrue();
        assertThat(access.allowedRentalIds()).isEmpty();
        assertThat(access.reason()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("차단 또는 제재 중 진행 거래가 있으면 해당 거래 메시지만 허용한다")
    void allowsOnlyExistingRentalWhenBlockedOrRestricted() {
        for (ChatRoomAccessPolicy.Restriction restriction : List.of(BLOCKED, RESTRICTED)) {
            ChatAccessResponse access = policy.resolve(1L, 2L, restriction, List.of("10"));
            assertThat(access.canSend()).isTrue();
            assertThat(access.allowedRentalIds()).containsExactly("10");
            assertThat(access.reason()).isEqualTo("EXISTING_RENTAL_ONLY");
        }
    }

    @Test
    @DisplayName("차단 또는 제재 중 진행 거래가 없으면 전송을 거부한다")
    void rejectsBlockedOrRestrictedWithoutExistingRental() {
        for (ChatRoomAccessPolicy.Restriction restriction : List.of(BLOCKED, RESTRICTED)) {
            ChatAccessResponse access = policy.resolve(1L, 2L, restriction, List.of());
            assertThat(access.canSend()).isFalse();
            assertThat(access.allowedRentalIds()).isEmpty();
            assertThat(access.reason()).isEqualTo(restriction.name());
        }
    }

    @Test
    @DisplayName("동네 변경 또는 물건 이용 불가 상태에서는 전송을 거부한다")
    void rejectsCommunityOrItemRestriction() {
        for (ChatRoomAccessPolicy.Restriction restriction :
            List.of(COMMUNITY_CHANGED, ITEM_UNAVAILABLE)) {
            ChatAccessResponse access = policy.resolve(1L, 2L, restriction, List.of("10"));
            assertThat(access.canSend()).isFalse();
            assertThat(access.allowedRentalIds()).isEmpty();
            assertThat(access.reason()).isEqualTo(restriction.name());
        }
    }
}
