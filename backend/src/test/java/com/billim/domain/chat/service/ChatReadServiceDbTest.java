package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ReadPositionRequest;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
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
@Import({ChatReadServiceDbTest.MySql.class, ChatReadService.class})
class ChatReadServiceDbTest {

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
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    @Autowired EntityManager em;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ChatReadService chatReadService;

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
    @DisplayName("읽은 위치는 감소하지 않고 마지막 메시지보다 앞으로 갈 수 없다")
    void readPositionNeverMovesBackwardOrPastLatestMessage() {
        sendText(OWNER_ID, "안녕하세요");

        assertThat(chatReadService.updateReadPosition(
            roomId, REQUESTER_ID, new ReadPositionRequest(1L)
        ).lastReadSequence()).isEqualTo(1L);

        assertThat(chatReadService.updateReadPosition(
            roomId, REQUESTER_ID, new ReadPositionRequest(0L)
        ).lastReadSequence()).isEqualTo(1L);

        assertThatThrownBy(() -> chatReadService.updateReadPosition(
            roomId, REQUESTER_ID, new ReadPositionRequest(2L)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
        );
    }

    @Test
    @DisplayName("참여하지 않은 회원은 방의 읽은 위치를 변경할 수 없다")
    void nonParticipantCannotUpdateReadPosition() {
        assertThatThrownBy(() -> chatReadService.updateReadPosition(
            roomId, 3L, new ReadPositionRequest(0L)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
        );
    }

    @Test
    @DisplayName("안 읽은 수는 읽은 위치 이후의 상대방 텍스트만 센다")
    void unreadCountExcludesOwnAndSystemMessages() {
        sendText(REQUESTER_ID, "제가 보낸 메시지");
        sendText(OWNER_ID, "첫 답장");
        sendText(OWNER_ID, "둘째 답장");

        chatReadService.updateReadPosition(
            roomId, REQUESTER_ID, new ReadPositionRequest(2L)
        );

        ChatRoom room = em.find(ChatRoom.class, roomId);
        long systemSequence = room.allocateSequence(NOW);
        em.flush();

        em.createNativeQuery("""
            INSERT INTO messages (room_id, sequence, type, created_at, updated_at)
            VALUES (?1, ?2, 'SYSTEM', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """)
            .setParameter(1, roomId)
            .setParameter(2, systemSequence)
            .executeUpdate();

        assertThat(chatReadService.getUnreadCount(REQUESTER_ID).unreadCount())
            .isEqualTo(1);
    }

    private void sendText(Long senderId, String body) {
        ChatRoom room = em.find(ChatRoom.class, roomId);
        em.persist(ChatMessage.text(
            roomId,
            room.allocateSequence(NOW),
            senderId,
            body,
            null,
            UUID.randomUUID(),
            NOW
        ));
        em.flush();
    }
}
