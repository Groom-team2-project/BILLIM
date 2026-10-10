package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.global.config.JpaAuditingConfig;
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
@Import({ChatRoomBaseServiceDbTest.MySql.class, ChatRoomBaseService.class, JpaAuditingConfig.class})
class ChatRoomBaseServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
        }
    }

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");

    @Autowired EntityManager em;
    @Autowired CategoryRepository categoryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ChatParticipantRepository participantRepository;
    @Autowired ChatRoomBaseService baseService;

    private Long roomId;
    private Long ownerId;
    private Long requesterId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        Member owner = memberRepository.save(Member.register("물건주인"));
        Member requester = memberRepository.save(Member.register("문의회원"));
        ownerId = owner.getId();
        requesterId = requester.getId();

        Long categoryId = categoryRepository
            .findByActiveTrueOrderBySortOrderAscIdAsc()
            .getFirst()
            .getId();
        Item item = Item.create(
            ownerId, 100L, categoryId, 10L, "전동드릴", null,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), NOW
        );
        em.persist(item);
        em.flush();
        itemId = item.getId();

        ChatRoom room = ChatRoom.open(itemId, ownerId, requesterId, NOW);
        em.persist(room);
        em.flush();
        roomId = room.getId();
        em.persist(ChatParticipant.join(roomId, ownerId, NOW));
        em.persist(ChatParticipant.join(roomId, requesterId, NOW));
        em.flush();
    }

    @Test
    @DisplayName("참여자는 실제 물건과 상대 회원 정보를 읽고 빈 방의 메시지 정보는 비어 있다")
    void returnsRoomBaseForParticipant() {
        ChatRoomBaseService.RoomBase result = baseService.get(roomId, requesterId);

        assertThat(result.id()).isEqualTo(String.valueOf(roomId));
        assertThat(result.itemId()).isEqualTo(String.valueOf(itemId));
        assertThat(result.itemTitle()).isEqualTo("전동드릴");
        assertThat(result.otherMember().id()).isEqualTo(String.valueOf(ownerId));
        assertThat(result.otherMember().displayName()).isEqualTo("물건주인");
        assertThat(result.otherMember().joinedAt()).isNotNull();
        assertThat(result.unreadCount()).isZero();
        assertThat(result.lastMessagePreview()).isNull();
        assertThat(result.lastMessageAt()).isNull();
    }

    @Test
    @DisplayName("안 읽은 수는 상대 메시지만 세고 마지막 메시지 미리보기는 200자로 제한한다")
    void countsUnreadAndLimitsPreview() {
        send(ownerId, "첫 답장", 1);
        send(requesterId, "내 메시지", 2);
        String longReply = "가".repeat(201);
        send(ownerId, longReply, 3);
        em.flush();

        ChatRoomBaseService.RoomBase beforeRead = baseService.get(roomId, requesterId);
        assertThat(beforeRead.unreadCount()).isEqualTo(2);
        assertThat(beforeRead.lastMessagePreview()).isEqualTo("가".repeat(200));
        assertThat(beforeRead.lastMessageAt()).isEqualTo(NOW.plusSeconds(3));

        ChatParticipant participant = participantRepository
            .findByRoomIdAndMemberId(roomId, requesterId).orElseThrow();
        participant.advanceReadSequence(1, 3, NOW.plusSeconds(4));
        em.flush();

        assertThat(baseService.get(roomId, requesterId).unreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("비참여자는 방 기본 정보를 조회할 수 없다")
    void rejectsNonParticipant() {
        Member outsider = memberRepository.save(Member.register("외부회원"));

        assertThatThrownBy(() -> baseService.get(roomId, outsider.getId()))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
            );
    }

    private void send(Long senderId, String body, int offsetSeconds) {
        ChatRoom room = em.find(ChatRoom.class, roomId);
        Instant sentAt = NOW.plusSeconds(offsetSeconds);
        em.persist(ChatMessage.text(
            roomId, room.allocateSequence(sentAt), senderId, body,
            null, UUID.randomUUID(), sentAt
        ));
    }
}
