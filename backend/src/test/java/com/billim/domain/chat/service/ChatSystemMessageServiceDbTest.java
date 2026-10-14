package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.MessageResponse;
import com.billim.domain.chat.dto.SystemPayloadResponse;
import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatMessageRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.json.JsonCodec;
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

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
    ChatSystemMessageServiceDbTest.MySql.class,
    ChatSystemMessageService.class,
    JsonCodec.class,
    JpaAuditingConfig.class
})
class ChatSystemMessageServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    @Autowired EntityManager em;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ChatMessageRepository messageRepository;
    @Autowired ChatSystemMessageService service;

    private Long roomId;
    private Long rentalId;
    private Long ownerId;
    private Long borrowerId;

    @BeforeEach
    void setUp() {
        ownerId = memberRepository.save(Member.register("물건 주인")).getId();
        borrowerId = memberRepository.save(Member.register("빌리는 이웃")).getId();
        Long categoryId = categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc()
            .getFirst().getId();
        Item item = Item.create(
            ownerId, 100L, categoryId, 10L, "전동드릴", null,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), NOW
        );
        em.persist(item);
        em.flush();

        ChatRoom room = ChatRoom.open(item.getId(), ownerId, borrowerId, NOW);
        em.persist(room);
        em.flush();
        roomId = room.getId();

        em.createNativeQuery("""
            insert into rentals (
                item_id, owner_id, borrower_id, community_id, chat_room_id,
                start_date, end_date, status, request_expires_at, due_at,
                item_title_snapshot, place_name_snapshot, place_id,
                created_at, updated_at
            ) values (
                :itemId, :ownerId, :borrowerId, 100, :roomId,
                '2026-10-14', '2026-10-15', 'APPROVED', NOW(6), NOW(6),
                '전동드릴', '정문', 10, NOW(6), NOW(6)
            )
            """)
            .setParameter("itemId", item.getId())
            .setParameter("ownerId", ownerId)
            .setParameter("borrowerId", borrowerId)
            .setParameter("roomId", roomId)
            .executeUpdate();
        rentalId = ((Number) em.createNativeQuery("select last_insert_id()")
            .getSingleResult()).longValue();
    }

    @Test
    @DisplayName("같은 사건을 재처리해도 시스템 메시지와 방 순번이 한 번만 증가한다")
    void reprocessingSameEventDoesNotDuplicateSystemMessage() {
        SystemPayloadResponse system = new SystemPayloadResponse(
            "RENTAL_APPROVED", 2, NOW, "대여 요청이 승인됐어요"
        );

        ChatMessage first = service.append(roomId, rentalId, null, 501L, system);
        ChatMessage repeated = service.append(roomId, rentalId, null, 501L, system);

        assertThat(repeated.getId()).isEqualTo(first.getId());
        assertThat(messageRepository.findAll()).hasSize(1);
        assertThat(first.getType()).isEqualTo(ChatMessage.Type.SYSTEM);
        assertThat(first.getSenderId()).isNull();
        assertThat(first.getSequence()).isEqualTo(1L);
        assertThat(MessageResponse.from(first).system()).isEqualTo(system);

        em.flush();
        em.clear();
        assertThat(em.find(ChatRoom.class, roomId).getNextSequence()).isEqualTo(2L);
    }

    @Test
    @DisplayName("서로 다른 사건은 같은 채팅방에서 연속된 순번을 받는다")
    void separateEventsReceiveConsecutiveRoomSequences() {
        ChatMessage first = service.append(roomId, rentalId, null, 501L,
            new SystemPayloadResponse("RENTAL_REQUESTED", 1, NOW, "대여 요청을 보냈어요"));
        ChatMessage second = service.append(roomId, rentalId, null, 502L,
            new SystemPayloadResponse("RENTAL_APPROVED", 2, NOW, "대여 요청이 승인됐어요"));

        assertThat(first.getSequence()).isEqualTo(1L);
        assertThat(second.getSequence()).isEqualTo(2L);
        assertThat(messageRepository.findAll()).hasSize(2);
    }
}
