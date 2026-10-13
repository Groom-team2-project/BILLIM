package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatMessage;
import com.billim.domain.chat.entity.ChatParticipant;
import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.entity.Rental;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
    ChatRoomListBaseServiceDbTest.MySql.class,
    ChatRoomListBaseService.class,
    ChatRoomLinkedRentalService.class,
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
    @Autowired ChatRoomLinkedRentalService linkedRentalService;

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

    @Test
    @DisplayName("안 읽은 방은 상대방의 미열람 TEXT 메시지가 있는 경우만 조회한다")
    void listsOnlyRoomsWithUnreadMessages() {
        ChatRoomListBaseService.RoomBasePage unread =
            listService.getUnread(requesterId, 0, 20);

        assertThat(roomIds(unread)).containsExactly(activeRoomId);
        assertThat(unread.totalElements()).isEqualTo(1);
        assertThat(unread.hasNext()).isFalse();
        assertThat(roomIds(listService.getUnread(ownerId, 0, 20))).isEmpty();

        ChatParticipant participant = em.createQuery("""
                select participant from ChatParticipant participant
                where participant.roomId = :roomId and participant.memberId = :memberId
                """, ChatParticipant.class)
            .setParameter("roomId", activeRoomId)
            .setParameter("memberId", requesterId)
            .getSingleResult();
        participant.advanceReadSequence(1, 1, NOW.plusSeconds(20));
        em.flush();
        em.clear();

        assertThat(roomIds(listService.getUnread(requesterId, 0, 20))).isEmpty();
    }

    @Test
    @DisplayName("안 읽은 방 조회는 잘못된 페이지 범위를 거부한다")
    void rejectsInvalidUnreadPageRequest() {
        assertThatThrownBy(() -> listService.getUnread(requesterId, -1, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> listService.getUnread(requesterId, 0, 51))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("연결 거래가 있는 방만 역할별로 조회하고 거래가 여럿이어도 중복하지 않는다")
    void listsRentalRoomsByRoleWithoutDuplicates() {
        createRental(firstRoomId);
        createRental(firstRoomId);
        createRental(activeRoomId);
        em.flush();
        em.clear();

        ChatRoomListBaseService.RoomBasePage borrowerFirst =
            listService.getBorrower(requesterId, 0, 1);
        assertThat(roomIds(borrowerFirst)).containsExactly(activeRoomId);
        assertThat(borrowerFirst.totalElements()).isEqualTo(2);
        assertThat(borrowerFirst.hasNext()).isTrue();

        ChatRoomListBaseService.RoomBasePage borrowerSecond =
            listService.getBorrower(requesterId, 1, 1);
        assertThat(roomIds(borrowerSecond)).containsExactly(firstRoomId);
        assertThat(borrowerSecond.totalElements()).isEqualTo(2);
        assertThat(borrowerSecond.hasNext()).isFalse();

        assertThat(roomIds(listService.getOwner(ownerId, 0, 20)))
            .containsExactly(activeRoomId, firstRoomId);
        assertThat(roomIds(listService.getBorrower(ownerId, 0, 20))).isEmpty();
        assertThat(roomIds(listService.getOwner(requesterId, 0, 20))).isEmpty();
    }

    @Test
    @DisplayName("방의 물건이나 거래 당사자가 다른 대여는 역할별 채팅방 목록에서 제외한다")
    void excludesRentalsNotMatchingRoomContext() {
        Long secondItemId = em.find(ChatRoom.class, secondRoomId).getItemId();
        Long activeItemId = em.find(ChatRoom.class, activeRoomId).getItemId();
        Long outsiderId = memberRepository.save(Member.register("다른대여자")).getId();

        createRental(firstRoomId, secondItemId, ownerId, requesterId);
        createRental(secondRoomId, secondItemId, ownerId, outsiderId);
        createRental(activeRoomId, activeItemId, outsiderId, requesterId);
        em.flush();
        em.clear();

        assertThat(roomIds(listService.getBorrower(requesterId, 0, 20))).isEmpty();
        assertThat(roomIds(listService.getOwner(ownerId, 0, 20))).isEmpty();
    }

    @Test
    @DisplayName("역할별 조회는 잘못된 회원과 페이지 범위를 거부한다")
    void rejectsInvalidRentalPageRequest() {
        assertThatThrownBy(() -> listService.getBorrower(null, 0, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> listService.getBorrower(requesterId, -1, 20))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThatThrownBy(() -> listService.getOwner(ownerId, 0, 51))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    }

    @Test
    @DisplayName("참여자에게 같은 물건과 당사자의 연결 거래만 최신순으로 반환한다")
    void listsOnlyLinkedRentalsForParticipant() {
        Long firstItemId = em.find(ChatRoom.class, firstRoomId).getItemId();
        Long secondItemId = em.find(ChatRoom.class, secondRoomId).getItemId();
        Long outsiderId = memberRepository.save(Member.register("다른대여자")).getId();

        createRental(firstRoomId);
        createRental(firstRoomId);
        createRental(firstRoomId, secondItemId, ownerId, requesterId);
        createRental(firstRoomId, firstItemId, ownerId, outsiderId);
        em.flush();
        em.clear();

        List<Rental> linked = linkedRentalService.get(firstRoomId, requesterId);
        assertThat(linked).hasSize(2)
            .allSatisfy(rental -> {
                assertThat(rental.getItemId()).isEqualTo(firstItemId);
                assertThat(rental.getOwnerId()).isEqualTo(ownerId);
                assertThat(rental.getBorrowerId()).isEqualTo(requesterId);
            })
            .isSortedAccordingTo(
                Comparator.comparing(Rental::getCreatedAt)
                    .thenComparing(Rental::getId)
                    .reversed()
            );
        assertThat(linkedRentalService.get(secondRoomId, requesterId)).isEmpty();
        assertThatThrownBy(() -> linkedRentalService.get(firstRoomId, outsiderId))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void createRental(Long roomId) {
        Long itemId = em.find(ChatRoom.class, roomId).getItemId();
        createRental(roomId, itemId, ownerId, requesterId);
    }

    private void createRental(Long roomId, Long itemId, Long owner, Long borrower) {
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
            .setParameter("ownerId", owner)
            .setParameter("borrowerId", borrower)
            .setParameter("roomId", roomId)
            .executeUpdate();
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
