package com.billim.domain.chat.service;

import com.billim.domain.chat.entity.ChatRoom;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.service.ItemAccessPolicy;
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
}
