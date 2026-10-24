package com.billim.domain.chat.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatDatabaseRelayTest {

    private final ChatWebSocketHandler handler = mock(ChatWebSocketHandler.class);
    private final ChatDatabaseRelay relay = new ChatDatabaseRelay(handler);

    @Test
    @DisplayName("현재 인스턴스에 연결된 방만 DB에서 다시 확인한다")
    void refreshesOnlyLocallyActiveRooms() {
        when(handler.activeRoomIds()).thenReturn(Set.of(901L, 902L));

        relay.deliverCommittedMessages();

        verify(handler).refreshRoom(901L);
        verify(handler).refreshRoom(902L);
        verifyNoMoreInteractions(handler);
    }
}
