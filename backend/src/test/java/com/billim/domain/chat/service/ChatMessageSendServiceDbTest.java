package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.SendMessageRequest;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
    ChatMessageSendServiceDbTest.MySql.class,
    ChatMessageSendService.class,
    ChatSendAccessPolicy.class
})
class ChatMessageSendServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
        }
    }

    private static final long OWNER_ID = 1L;
    private static final long REQUESTER_ID = 2L;
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Autowired EntityManager em;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ChatMessageRepository messageRepository;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired ChatMessageSendService sendService;

    private Long roomId;

    @BeforeEach
    void setUp() {
        Long categoryId = categoryRepository
            .findByActiveTrueOrderBySortOrderAscIdAsc()
            .getFirst()
            .getId();

        Item item = Item.create(
            OWNER_ID, 100L, categoryId, 10L, "전동드릴", null,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31),
            NOW
        );
        em.persist(item);
        em.flush();

        ChatRoom room = ChatRoom.open(item.getId(), OWNER_ID, REQUESTER_ID, NOW);
        em.persist(room);
        em.flush();
        roomId = room.getId();

        em.persist(ChatParticipant.join(roomId, OWNER_ID, NOW));
        em.persist(ChatParticipant.join(roomId, REQUESTER_ID, NOW));
        em.flush();
    }

    @Test
    @DisplayName("참여자가 보낸 텍스트 메시지를 저장하고 방 순번을 증가시킨다")
    void savesMessageAndAdvancesRoomSequence() {
        UUID clientMessageId = UUID.randomUUID();

        ChatMessage saved = sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(clientMessageId, "안녕하세요", null)
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSequence()).isEqualTo(1L);
        assertThat(saved.getSenderId()).isEqualTo(REQUESTER_ID);
        assertThat(saved.getClientMessageId()).isEqualTo(clientMessageId.toString());
        assertThat(messageRepository.count()).isEqualTo(1L);

        em.clear();
        ChatRoom room = roomRepository.findById(roomId).orElseThrow();
        assertThat(room.getNextSequence()).isEqualTo(2L);
        assertThat(room.getLastMessageAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 메시지를 재전송하면 기존 메시지를 반환하고 중복 저장하지 않는다")
    void returnsExistingMessageForSameRetry() {
        UUID clientMessageId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest(
            clientMessageId,
            "응답을 받지 못해 다시 보냅니다",
            null
        );

        ChatMessage first = sendService.send(roomId, REQUESTER_ID, request);
        ChatMessage retried = sendService.send(roomId, REQUESTER_ID, request);

        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(retried.getSequence()).isEqualTo(first.getSequence());
        assertThat(messageRepository.count()).isEqualTo(1L);

        em.clear();
        assertThat(roomRepository.findById(roomId).orElseThrow().getNextSequence())
            .isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 메시지 키를 다른 내용으로 재사용하면 거부한다")
    void rejectsReusedKeyWithDifferentContent() {
        UUID clientMessageId = UUID.randomUUID();
        sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(clientMessageId, "처음 내용", null)
        );

        assertThatThrownBy(() -> sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(clientMessageId, "바뀐 내용", null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_KEY_REUSED)
        );
    }

    @Test
    @DisplayName("비참여자와 검증할 수 없는 거래 ID의 메시지 전송을 거부한다")
    void rejectsNonParticipantAndUnverifiedRental() {
        assertThatThrownBy(() -> sendService.send(
            roomId,
            3L,
            new SendMessageRequest(UUID.randomUUID(), "비참여자 메시지", null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
        );

        assertThatThrownBy(() -> sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(UUID.randomUUID(), "거래 메시지", "501")
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
        );
    }
}
