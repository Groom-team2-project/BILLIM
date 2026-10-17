package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.service.ItemAccessPolicy;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({ChatRoomOpenServiceDbTest.MySql.class, ChatRoomOpenService.class})
class ChatRoomOpenServiceDbTest {

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

    @Autowired EntityManager em;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired ChatParticipantRepository participantRepository;
    @Autowired ChatRoomOpenService openService;
    @MockitoBean ItemAccessPolicy accessPolicy;
    @MockitoBean CommunityMembershipQuery membershipQuery;
    @MockitoBean BlockQuery blockQuery;
    @MockitoBean SanctionService sanctionService;

    private Long itemId;

    @BeforeEach
    void setUp() {
        Long categoryId = categoryRepository
            .findByActiveTrueOrderBySortOrderAscIdAsc()
            .getFirst()
            .getId();

        Item item = Item.create(
            OWNER_ID, 100L, categoryId, 10L, "전동드릴", null,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
            Instant.parse("2026-10-10T00:00:00Z")
        );
        em.persist(item);
        em.flush();
        itemId = item.getId();
        when(membershipQuery.findActiveCommunityId(REQUESTER_ID))
            .thenReturn(Optional.of(100L));
    }

    @Test
    @DisplayName("문의 방을 만들고 같은 문의를 반복하면 기존 방과 참여자 두 명을 재사용한다")
    void createsAndReusesRoom() {
        when(accessPolicy.canView(any(Item.class), eq(REQUESTER_ID))).thenReturn(true);

        ChatRoom created = openService.open(itemId, REQUESTER_ID);
        ChatRoom reopened = openService.open(itemId, REQUESTER_ID);

        assertThat(reopened.getId()).isEqualTo(created.getId());
        assertThat(roomRepository.count()).isEqualTo(1);
        assertThat(participantRepository.count()).isEqualTo(2);
        assertThat(participantRepository.findByRoomIdAndMemberId(created.getId(), OWNER_ID)).isPresent();
        assertThat(participantRepository.findByRoomIdAndMemberId(created.getId(), REQUESTER_ID)).isPresent();
    }

    @Test
    @DisplayName("본인 물건과 조회할 수 없는 물건에는 문의 방을 만들지 않는다")
    void rejectsOwnerAndInaccessibleItem() {
        assertThatThrownBy(() -> openService.open(itemId, OWNER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN)
            );

        when(accessPolicy.canView(any(Item.class), eq(REQUESTER_ID))).thenReturn(false);
        assertThatThrownBy(() -> openService.open(itemId, REQUESTER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND)
            );

        assertThat(roomRepository.count()).isZero();
        assertThat(participantRepository.count()).isZero();
    }

    @Test
    @DisplayName("제재된 요청자는 새 문의 방을 만들 수 없다")
    void restrictedRequesterCannotCreateRoom() {
        when(accessPolicy.canView(any(Item.class), eq(REQUESTER_ID))).thenReturn(true);
        when(sanctionService.isRestricted(REQUESTER_ID)).thenReturn(true);

        assertThatThrownBy(() -> openService.open(itemId, REQUESTER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_RESTRICTED));
        assertThat(roomRepository.count()).isZero();
        assertThat(participantRepository.count()).isZero();
    }

    @Test
    @DisplayName("문의 방이 이미 있어도 제재된 요청자는 열기 요청으로 재사용할 수 없다")
    void restrictedRequesterCannotReopenRoom() {
        when(accessPolicy.canView(any(Item.class), eq(REQUESTER_ID))).thenReturn(true);
        ChatRoom existing = openService.open(itemId, REQUESTER_ID);
        when(sanctionService.isRestricted(REQUESTER_ID)).thenReturn(true);

        assertThatThrownBy(() -> openService.open(itemId, REQUESTER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_RESTRICTED));
        assertThat(roomRepository.count()).isEqualTo(1);
        assertThat(participantRepository.findByRoomIdAndMemberId(
            existing.getId(), REQUESTER_ID)).isPresent();
    }

    @Test
    @DisplayName("유효한 동네 소속이 없으면 문의 방을 만들지 않는다")
    void invalidMembershipCannotCreateRoom() {
        when(accessPolicy.canView(any(Item.class), eq(REQUESTER_ID))).thenReturn(true);
        when(membershipQuery.findActiveCommunityId(REQUESTER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> openService.open(itemId, REQUESTER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        assertThat(roomRepository.count()).isZero();
    }

    @Test
    @DisplayName("차단 관계이면 문의 방을 새로 만들지 않는다")
    void blockedRequesterCannotCreateRoom() {
        when(accessPolicy.canView(any(Item.class), eq(REQUESTER_ID))).thenReturn(true);
        when(blockQuery.isBlockedBetween(REQUESTER_ID, OWNER_ID)).thenReturn(true);

        assertThatThrownBy(() -> openService.open(itemId, REQUESTER_ID))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BLOCKED_RELATION));
        assertThat(roomRepository.count()).isZero();
    }

    @Test
    @DisplayName("방을 나중에 열면 같은 물건과 당사자의 기존 거래만 연결한다")
    void openingRoomLinksOnlyMatchingExistingRentals() {
        em.createNativeQuery("""
            insert into members (id, display_name, created_at, updated_at)
            values (1, '소유자', now(6), now(6)),
                   (2, '요청자', now(6), now(6)),
                   (3, '다른 요청자', now(6), now(6))
            """).executeUpdate();
        insertRental(9101L, REQUESTER_ID);
        insertRental(9102L, 3L);
        when(accessPolicy.canView(any(Item.class), eq(REQUESTER_ID))).thenReturn(true);

        Long roomId = openService.open(itemId, REQUESTER_ID).getId();
        openService.open(itemId, REQUESTER_ID);

        Number linkedRoomId = (Number) em.createNativeQuery(
            "select chat_room_id from rentals where id = 9101"
        ).getSingleResult();
        assertThat(linkedRoomId.longValue()).isEqualTo(roomId);
        assertThat(em.createNativeQuery(
            "select chat_room_id from rentals where id = 9102"
        ).getSingleResult()).isNull();
        assertThat(roomRepository.count()).isEqualTo(1);
    }

    private void insertRental(Long rentalId, Long borrowerId) {
        em.createNativeQuery("""
            insert into rentals (
                id, item_id, owner_id, borrower_id, community_id,
                start_date, end_date, status, request_expires_at, due_at,
                item_title_snapshot, place_name_snapshot, place_id,
                created_at, updated_at
            ) values (
                :rentalId, :itemId, :ownerId, :borrowerId, 100,
                '2026-10-20', '2026-10-21', 'REQUESTED',
                '2026-10-19 15:00:00', '2026-10-21 15:00:00',
                '전동드릴', '정문', 10, now(6), now(6)
            )
            """)
            .setParameter("rentalId", rentalId)
            .setParameter("itemId", itemId)
            .setParameter("ownerId", OWNER_ID)
            .setParameter("borrowerId", borrowerId)
            .executeUpdate();
    }
}
