package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.SendMessageRequest;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.safety.service.BlockQuery;
import com.billim.domain.safety.service.SanctionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
    @MockitoBean CommunityMembershipQuery membershipQuery;
    @MockitoBean BlockQuery blockQuery;
    @MockitoBean SanctionService sanctionService;

    private Long roomId;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("""
                insert into members (
                    id, display_name, role, status, active_community_id,
                    version, created_at, updated_at
                ) values
                    (1, '소유자', 'USER', 'ACTIVE', 100, 0, NOW(6), NOW(6)),
                    (2, '요청자', 'USER', 'ACTIVE', 100, 0, NOW(6), NOW(6))
                """).executeUpdate();

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
        when(membershipQuery.findActiveCommunityId(REQUESTER_ID))
            .thenReturn(Optional.of(100L));
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
    @DisplayName("메시지 저장 뒤 물건이 비공개로 바뀌어도 같은 메시지 재시도는 기존 결과를 반환한다")
    void retryAfterItemHiddenReturnsExistingMessage() {
        UUID clientMessageId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest(clientMessageId, "이미 보낸 메시지", null);
        ChatMessage first = sendService.send(roomId, REQUESTER_ID, request);

        Long itemId = roomRepository.findById(roomId).orElseThrow().getItemId();
        Item item = em.find(Item.class, itemId);
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        em.flush();

        ChatMessage retried = sendService.send(roomId, REQUESTER_ID, request);

        assertThat(retried.getId()).isEqualTo(first.getId());
        assertThat(retried.getSequence()).isEqualTo(first.getSequence());
        assertThat(messageRepository.count()).isEqualTo(1L);
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

    @Test
    @DisplayName("활성 동네가 달라진 참여자의 메시지는 저장하지 않는다")
    void rejectsMessageAfterCommunityChange() {
        when(membershipQuery.findActiveCommunityId(REQUESTER_ID))
            .thenReturn(Optional.of(999L));

        assertThatThrownBy(() -> sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(UUID.randomUUID(), "다른 동네에서 보낸 메시지", null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED)
        );
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    @DisplayName("비공개 물건의 채팅방에서는 메시지를 저장하지 않는다")
    void rejectsMessageForHiddenItem() {
        Long itemId = roomRepository.findById(roomId).orElseThrow().getItemId();
        Item item = em.find(Item.class, itemId);
        item.changeVisibility(ItemVisibility.HIDDEN, NOW);
        em.flush();

        assertThatThrownBy(() -> sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(UUID.randomUUID(), "비공개 물건 메시지", null)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
        );
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    @DisplayName("방에 연결된 거래 메시지는 허용하고 물건이 다른 거래는 거부한다")
    void acceptsOnlyRentalLinkedToRoomItem() {
        Long itemId = roomRepository.findById(roomId).orElseThrow().getItemId();
        Long categoryId = categoryRepository
            .findByActiveTrueOrderBySortOrderAscIdAsc()
            .getFirst()
            .getId();
        Item otherItem = Item.create(
            OWNER_ID, 100L, categoryId, 10L, "다른 물건", null,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31),
            NOW
        );
        em.persist(otherItem);
        em.flush();

        Long linkedRentalId = createRental(itemId, REQUESTER_ID);
        Long otherRentalId = createRental(otherItem.getId(), REQUESTER_ID);

        ChatMessage saved = sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(UUID.randomUUID(), "거래 관련 메시지", linkedRentalId.toString())
        );

        assertThat(saved.getRentalId()).isEqualTo(linkedRentalId);
        assertThatThrownBy(() -> sendService.send(
            roomId,
            REQUESTER_ID,
            new SendMessageRequest(UUID.randomUUID(), "다른 거래 메시지", otherRentalId.toString())
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
        );
        assertThat(messageRepository.count()).isEqualTo(1L);
    }

    private Long createRental(Long itemId, Long borrowerId) {
        em.createNativeQuery("""
                insert into rentals (
                    item_id, owner_id, borrower_id, community_id, chat_room_id,
                    start_date, end_date, status, request_expires_at, due_at,
                    item_title_snapshot, place_name_snapshot, place_id,
                    created_at, updated_at
                ) values (
                    :itemId, :ownerId, :borrowerId, 100, :roomId,
                    '2026-10-10', '2026-10-11', 'REQUESTED', NOW(6), NOW(6),
                    '전동드릴', '정문', 10, NOW(6), NOW(6)
                )
                """)
            .setParameter("itemId", itemId)
            .setParameter("ownerId", OWNER_ID)
            .setParameter("borrowerId", borrowerId)
            .setParameter("roomId", roomId)
            .executeUpdate();
        return ((Number) em.createNativeQuery("select last_insert_id()")
            .getSingleResult()).longValue();
    }
}
