package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.MessagePageResponse;
import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatParticipantRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({ChatMessageQueryServiceDbTest.MySql.class, ChatMessageQueryService.class})
class ChatMessageQueryServiceDbTest {

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
    @Autowired ChatParticipantRepository participantRepository;
    @Autowired ChatMessageQueryService queryService;

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
    @DisplayName("기본 조회는 최신순이고 이전 커서로 모든 메시지를 중복 없이 읽는다")
    void latestAndBeforeCursorReturnEveryMessageOnce() {
        sendFiveMessages();

        ChatParticipant owner = participantRepository
            .findByRoomIdAndMemberId(roomId, OWNER_ID).orElseThrow();
        owner.advanceReadSequence(2, 5, NOW.plusSeconds(1));
        em.flush();

        MessagePageResponse first = queryService.getMessages(roomId, REQUESTER_ID, null, null, 2);
        assertThat(sequences(first)).containsExactly(5L, 4L);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextBeforeSequence()).isEqualTo(4L);
        assertThat(first.nextAfterSequence()).isNull();
        assertThat(first.otherLastReadSequence()).isEqualTo(2L);

        MessagePageResponse second = queryService.getMessages(
            roomId, REQUESTER_ID, null, first.nextBeforeSequence(), 2
        );
        assertThat(sequences(second)).containsExactly(3L, 2L);
        assertThat(second.nextBeforeSequence()).isEqualTo(2L);

        MessagePageResponse last = queryService.getMessages(
            roomId, REQUESTER_ID, null, second.nextBeforeSequence(), 2
        );
        assertThat(sequences(last)).containsExactly(1L);
        assertThat(last.hasMore()).isFalse();
        assertThat(last.nextBeforeSequence()).isNull();
    }

    @Test
    @DisplayName("새 메시지 커서는 오름차순으로 조회하고 다음 커서를 반환한다")
    void afterCursorReturnsAscendingMessages() {
        sendFiveMessages();

        MessagePageResponse first = queryService.getMessages(roomId, REQUESTER_ID, 1L, null, 2);
        assertThat(sequences(first)).containsExactly(2L, 3L);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextAfterSequence()).isEqualTo(3L);
        assertThat(first.nextBeforeSequence()).isNull();

        MessagePageResponse last = queryService.getMessages(
            roomId, REQUESTER_ID, first.nextAfterSequence(), null, 2
        );
        assertThat(sequences(last)).containsExactly(4L, 5L);
        assertThat(last.hasMore()).isFalse();
        assertThat(last.nextAfterSequence()).isNull();
    }

    @Test
    @DisplayName("참여하지 않은 회원은 대화를 읽을 수 없고 잘못된 커서는 거부한다")
    void rejectsNonParticipantAndInvalidCursor() {
        assertThatThrownBy(() -> queryService.getMessages(roomId, 3L, null, null, 2))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
            );

        assertThatThrownBy(() -> queryService.getMessages(roomId, REQUESTER_ID, 1L, 2L, 2))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
            );

        assertThatThrownBy(() -> queryService.getMessages(roomId, REQUESTER_ID, null, null, 51))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
            );
    }

    private void sendFiveMessages() {
        for (int i = 1; i <= 5; i++) {
            ChatRoom room = em.find(ChatRoom.class, roomId);
            em.persist(ChatMessage.text(
                roomId,
                room.allocateSequence(NOW.plusSeconds(i)),
                i % 2 == 0 ? OWNER_ID : REQUESTER_ID,
                "메시지 " + i,
                null,
                UUID.randomUUID(),
                NOW.plusSeconds(i)
            ));
        }
        em.flush();
    }

    private List<Long> sequences(MessagePageResponse page) {
        return page.items().stream().map(MessageResponse::sequence).toList();
    }
}
