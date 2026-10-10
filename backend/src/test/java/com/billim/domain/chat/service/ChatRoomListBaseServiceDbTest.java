package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
    ChatRoomListBaseServiceDbTest.MySql.class,
    ChatRoomListBaseService.class,
    ChatRoomBaseService.class,
    JpaAuditingConfig.class
})
class ChatRoomListBaseServiceDbTest {

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
    @Autowired MemberRepository memberRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ChatRoomListBaseService listService;

    private Long ownerId;
    private Long requesterId;
    private Long firstRoomId;
    private Long secondRoomId;
    private Long activeRoomId;

    @BeforeEach
    void setUp() {
        ownerId = memberRepository.save(Member.register("물건주인")).getId();
        requesterId = memberRepository.save(Member.register("문의회원")).getId();
        Long outsiderId = memberRepository.save(Member.register("외부회원")).getId();
        Long categoryId = categoryRepository
            .findByActiveTrueOrderBySortOrderAscIdAsc()
            .getFirst()
            .getId();

        firstRoomId = createRoom(ownerId, requesterId, categoryId, NOW);
        secondRoomId = createRoom(ownerId, requesterId, categoryId, NOW);
        activeRoomId = createRoom(ownerId, requesterId, categoryId, NOW);
        createRoom(ownerId, outsiderId, categoryId, NOW.plusSeconds(20));

        ChatRoom activeRoom = em.find(ChatRoom.class, activeRoomId);
        Instant sentAt = NOW.plusSeconds(10);
        em.persist(ChatMessage.text(
            activeRoomId,
            activeRoom.allocateSequence(sentAt),
            ownerId,
            "새 메시지",
            null,
            UUID.randomUUID(),
            sentAt
        ));
        em.flush();
    }

    @Test
    @DisplayName("본인 방만 최근 활동과 ID 역순으로 조회하고 페이지 정보를 반환한다")
    void ordersParticipatingRoomsWithStablePages() {
        ChatRoomListBaseService.RoomBasePage first = listService.getAll(requesterId, 0, 2);

        assertThat(roomIds(first)).containsExactly(activeRoomId, secondRoomId);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);

        ChatRoomListBaseService.RoomBasePage second = listService.getAll(requesterId, 1, 2);
        assertThat(roomIds(second)).containsExactly(firstRoomId);
        assertThat(second.totalElements()).isEqualTo(3);
        assertThat(second.hasNext()).isFalse();
    }

    @Test
    @DisplayName("잘못된 페이지 범위는 조회하지 않는다")
    void rejectsInvalidPageRequest() {
        assertThatThrownBy(() -> listService.getAll(requesterId, -1, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
            );
        assertThatThrownBy(() -> listService.getAll(requesterId, 0, 51))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST)
            );
    }

    private Long createRoom(Long owner, Long requester, Long categoryId, Instant createdAt) {
        Item item = Item.create(
            owner, 100L, categoryId, 10L, "전동드릴", null,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), createdAt
        );
        em.persist(item);
        em.flush();

        ChatRoom room = ChatRoom.open(item.getId(), owner, requester, createdAt);
        em.persist(room);
        em.flush();
        em.persist(ChatParticipant.join(room.getId(), owner, createdAt));
        em.persist(ChatParticipant.join(room.getId(), requester, createdAt));
        em.flush();
        return room.getId();
    }

    private List<Long> roomIds(ChatRoomListBaseService.RoomBasePage page) {
        return page.items().stream().map(item -> Long.parseLong(item.id())).toList();
    }
}
