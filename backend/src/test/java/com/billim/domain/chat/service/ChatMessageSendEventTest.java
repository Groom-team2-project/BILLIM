package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.SendMessageRequest;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageSendEventTest {

    private final ChatRoomRepository roomRepository = mock(ChatRoomRepository.class);
    private final ChatMessageRepository messageRepository = mock(ChatMessageRepository.class);
    private final ChatSendAccessPolicy accessPolicy = mock(ChatSendAccessPolicy.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ChatMessageSendService sendService = new ChatMessageSendService(
        roomRepository, messageRepository, accessPolicy, eventPublisher);

    @Test
    @DisplayName("새 메시지는 저장된 ID를 포함한 응답 이벤트를 발행한다")
    void newMessagePublishesResponseEvent() {
        UUID clientMessageId = UUID.randomUUID();
        ChatRoom room = mock(ChatRoom.class);
        when(room.isParticipant(2L)).thenReturn(true);
        when(roomRepository.findLockedById(901L)).thenReturn(Optional.of(room));
        when(room.allocateSequence(any(Instant.class))).thenReturn(1L);
        when(messageRepository.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            ReflectionTestUtils.setField(message, "id", 701L);
            return message;
        });

        sendService.send(901L, 2L, new SendMessageRequest(clientMessageId, "안녕하세요", null));

        org.mockito.ArgumentCaptor<ChatMessageCreatedEvent> eventCaptor =
            org.mockito.ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ChatMessageCreatedEvent event = eventCaptor.getValue();
        assertThat(event.roomId()).isEqualTo(901L);
        assertThat(event.message().id()).isEqualTo("701");
        assertThat(event.message().roomId()).isEqualTo("901");
        assertThat(event.message().body()).isEqualTo("안녕하세요");
        assertThat(event.message().clientMessageId()).isEqualTo(clientMessageId);
    }

    @Test
    @DisplayName("동일한 메시지 재전송은 실시간 이벤트를 다시 발행하지 않는다")
    void retryDoesNotRepublishEvent() {
        UUID clientMessageId = UUID.randomUUID();
        ChatRoom room = mock(ChatRoom.class);
        ChatMessage existing = ChatMessage.text(
            901L, 1L, 2L, "안녕하세요", null, clientMessageId, Instant.now());
        when(room.isParticipant(2L)).thenReturn(true);
        when(roomRepository.findLockedById(901L)).thenReturn(Optional.of(room));
        when(messageRepository.findByRoomIdAndSenderIdAndClientMessageId(
            eq(901L), eq(2L), eq(clientMessageId.toString())))
            .thenReturn(Optional.of(existing));

        ChatMessage result = sendService.send(
            901L, 2L, new SendMessageRequest(clientMessageId, "안녕하세요", null));

        assertThat(result).isSameAs(existing);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
