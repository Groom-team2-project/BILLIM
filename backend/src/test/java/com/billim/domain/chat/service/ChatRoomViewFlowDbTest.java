package com.billim.domain.chat.service;

import com.billim.domain.chat.dto.ChatRoomPageResponse;
import com.billim.domain.chat.dto.ChatRoomResponse;
import com.billim.domain.chat.repository.ChatParticipantRepository;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.community.service.CommunityMembershipService;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.entity.ItemVisibility;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.repository.RentalRepository;
import com.billim.domain.safety.service.BlockService;
import com.billim.domain.safety.service.SanctionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.profiles.active=perf",
    "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
    "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
    "billim.notification.outbox.enabled=false"
})
@Import(ChatRoomViewFlowDbTest.MySql.class)
class ChatRoomViewFlowDbTest {

    private static final long COMMUNITY_ID = 100L;

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
        }
    }

    @Autowired ChatRoomViewService viewService;
    @Autowired ChatRoomRepository roomRepository;
    @Autowired ChatParticipantRepository participantRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired RentalRepository rentalRepository;
    @MockitoBean CommunityMembershipService membershipQuery;
    @MockitoBean BlockService blockQuery;
    @MockitoBean SanctionService sanctionService;
    @MockitoSpyBean ChatRoomBaseService baseService;

    private Long ownerId;
    private Long requesterId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        ownerId = memberRepository.save(Member.register("물건주인")).getId();
        requesterId = memberRepository.save(Member.register("문의회원")).getId();
        Long categoryId = categoryRepository
            .findByActiveTrueOrderBySortOrderAscIdAsc().getFirst().getId();
        Item item = Item.create(
            ownerId, COMMUNITY_ID, categoryId, 10L, "전동드릴", null,
            LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), Instant.now()
        );
        itemId = itemRepository.save(item).getId();
        when(membershipQuery.findActiveCommunityId(requesterId))
            .thenReturn(Optional.of(COMMUNITY_ID));
    }

    @Test
    @DisplayName("빈 채팅방 목록과 생성·상세·결과 있는 목록이 모두 응답한다")
    void opensAndListsRoomWithoutConversionFailure() {
        ChatRoomPageResponse empty = viewService.list(requesterId, "ALL", 0, 20);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.totalElements()).isZero();

        ChatRoomResponse opened = viewService.open(itemId, requesterId);
        Long roomId = Long.valueOf(opened.id());
        ChatRoomResponse detail = viewService.get(roomId, requesterId);
        ChatRoomPageResponse listed = viewService.list(requesterId, "ALL", 0, 20);

        assertThat(opened.itemId()).isEqualTo(String.valueOf(itemId));
        assertThat(opened.rentals()).isEmpty();
        assertThat(opened.access().canSend()).isTrue();
        assertThat(opened.access().reason()).isEqualTo("NORMAL");
        assertThat(detail.id()).isEqualTo(opened.id());
        assertThat(listed.items()).extracting(ChatRoomResponse::id).containsExactly(opened.id());
        assertThat(listed.totalElements()).isEqualTo(1);
        assertThat(roomRepository.findByItemIdAndRequesterId(itemId, requesterId)).isPresent();
        assertThat(participantRepository.findByRoomIdAndMemberId(roomId, ownerId)).isPresent();
        assertThat(participantRepository.findByRoomIdAndMemberId(roomId, requesterId)).isPresent();
    }

    @Test
    @DisplayName("연결 거래 요약과 차단·제재의 기존 거래 예외 및 동네·물건 제한을 반환한다")
    void returnsLinkedRentalAndRestrictionContract() {
        Instant now = Instant.now();
        LocalDate startDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);
        Rental rental = Rental.request(
            itemId, ownerId, requesterId, COMMUNITY_ID,
            startDate, startDate.plusDays(1), now.plusSeconds(3600),
            now.plusSeconds(86400 * 4), "전동드릴", 10L, "정문"
        );
        rental.approve(now);
        Long rentalId = rentalRepository.save(rental).getId();

        ChatRoomResponse opened = viewService.open(itemId, requesterId);
        Long roomId = Long.valueOf(opened.id());
        assertThat(opened.rentals()).hasSize(1);
        assertThat(opened.rentals().getFirst().id()).isEqualTo(String.valueOf(rentalId));
        assertThat(opened.rentals().getFirst().status()).isEqualTo(RentalStatus.APPROVED);
        assertThat(opened.access().allowedRentalIds()).containsExactly(String.valueOf(rentalId));

        when(blockQuery.isBlockedBetween(requesterId, ownerId)).thenReturn(true);
        ChatRoomResponse blocked = viewService.get(roomId, requesterId);
        assertThat(blocked.access().canSend()).isTrue();
        assertThat(blocked.access().allowedRentalIds()).containsExactly(String.valueOf(rentalId));
        assertThat(blocked.access().reason()).isEqualTo("EXISTING_RENTAL_ONLY");

        when(blockQuery.isBlockedBetween(requesterId, ownerId)).thenReturn(false);
        when(sanctionService.isRestricted(requesterId)).thenReturn(true);
        assertThat(viewService.get(roomId, requesterId).access().reason())
            .isEqualTo("EXISTING_RENTAL_ONLY");

        when(membershipQuery.findActiveCommunityId(requesterId))
            .thenReturn(Optional.empty());
        ChatRoomResponse changed = viewService.get(roomId, requesterId);
        assertThat(changed.access().canSend()).isFalse();
        assertThat(changed.access().allowedRentalIds()).isEmpty();
        assertThat(changed.access().reason()).isEqualTo("COMMUNITY_CHANGED");

        when(membershipQuery.findActiveCommunityId(requesterId))
            .thenReturn(Optional.of(COMMUNITY_ID));
        Item item = itemRepository.findById(itemId).orElseThrow();
        item.changeVisibility(ItemVisibility.HIDDEN, Instant.now());
        itemRepository.save(item);
        ChatRoomResponse hidden = viewService.get(roomId, requesterId);
        assertThat(hidden.access().canSend()).isFalse();
        assertThat(hidden.access().reason()).isEqualTo("ITEM_UNAVAILABLE");
    }

    @Test
    @DisplayName("응답 변환이 실패하면 새 채팅방과 참여자 저장을 롤백한다")
    void rollsBackRoomWhenResponseConversionFails() {
        long participantsBefore = participantRepository.count();
        doThrow(new IllegalStateException("응답 변환 실패"))
            .when(baseService).get(anyLong(), eq(requesterId));

        assertThatThrownBy(() -> viewService.open(itemId, requesterId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("응답 변환 실패");

        assertThat(roomRepository.findByItemIdAndRequesterId(itemId, requesterId)).isEmpty();
        assertThat(participantRepository.count()).isEqualTo(participantsBefore);
    }
}
