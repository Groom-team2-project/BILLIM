package com.billim.domain.chat.service;

import com.billim.MockChatRedisListener;
import com.billim.domain.chat.dto.SendMessageRequest;
import com.billim.domain.chat.repository.ChatRoomRepository;
import com.billim.domain.community.entity.Community;
import com.billim.domain.community.entity.CommunityMembership;
import com.billim.domain.community.entity.CommunityStatus;
import com.billim.domain.community.repository.CommunityMembershipRepository;
import com.billim.domain.community.repository.CommunityRepository;
import com.billim.domain.community.service.CommunityMembershipQuery;
import com.billim.domain.community.service.MyCommunityService;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.entity.NotificationTargetType;
import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.service.NotificationQueryService;
import com.billim.domain.notification.service.NotificationReadService;
import com.billim.domain.safety.service.BlockService;
import com.billim.domain.safety.service.SanctionService;
import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportAction;
import com.billim.domain.safety.entity.ReportActionType;
import com.billim.domain.safety.entity.ReportReasonCode;
import com.billim.domain.safety.entity.ReportTargetType;
import com.billim.domain.safety.entity.Sanction;
import com.billim.domain.rental.entity.Rental;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.profiles.active=perf",
    "spring.security.oauth2.client.registration.kakao.client-id=test",
    "spring.security.oauth2.client.registration.kakao.client-secret=test",
    "billim.notification.outbox.enabled=false"
})
@Import(ChatSavedCommunityFlowDbTest.MySql.class)
@MockChatRedisListener
@Transactional
class ChatSavedCommunityFlowDbTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean @ServiceConnection
        MySQLContainer mysql() { return new MySQLContainer("mysql:8.4"); }
    }

    @Autowired MemberRepository members;
    @Autowired CommunityRepository communities;
    @Autowired CommunityMembershipRepository memberships;
    @Autowired MyCommunityService myCommunities;
    @Autowired CommunityMembershipQuery membershipQuery;
    @Autowired CategoryRepository categories;
    @Autowired ItemRepository items;
    @Autowired ChatRoomRepository rooms;
    @Autowired ChatRoomViewService view;
    @Autowired ChatMessageSendService messages;
    @Autowired NotificationQueryService notifications;
    @Autowired NotificationReadService notificationReads;
    @Autowired EntityManager em;
    @MockitoBean BlockService blocks;
    @MockitoBean SanctionService sanctions;
    private Long owner;
    private Long borrower;
    private Long communityA;
    private Long communityB;
    private Long itemA;
    private Long itemB;
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        owner = members.saveAndFlush(Member.register("동네 소유자")).getId();
        borrower = members.saveAndFlush(Member.register("동네 요청자")).getId();
        communityA = community("가동");
        communityB = community("나동");
        for (Long member : List.of(owner, borrower)) {
            memberships.saveAllAndFlush(List.of(
                CommunityMembership.verified(member, communityA, 1, now),
                CommunityMembership.verified(member, communityB, 2, now)));
            myCommunities.select(member, communityB);
        }
        itemA = item(communityA);
        itemB = item(communityB);
    }

    @Test
    @DisplayName("현재 동네를 전환해도 양쪽 저장 동네의 방 생성·목록·상세·전송 허용")
    void switchingCurrentCommunityPreservesBothChats() {
        var roomA = view.open(itemA, borrower);
        var roomB = view.open(itemB, borrower);
        assertThat(membershipQuery.findActiveCommunityId(borrower)).contains(communityB);
        assertThat(view.list(borrower, "ALL", 0, 20).items())
            .hasSize(2).allSatisfy(room -> {
                assertThat(room.access().canSend()).isTrue();
                assertThat(room.access().reason()).isEqualTo("NORMAL");
            });
        for (Long member : List.of(owner, borrower)) {
            assertThat(send(Long.valueOf(roomA.id()), member).getSequence()).isPositive();
            assertThat(view.get(Long.valueOf(roomA.id()), member).access().canSend()).isTrue();
        }
        myCommunities.select(borrower, communityA);
        assertThat(view.open(itemB, borrower).id()).isEqualTo(roomB.id());
        assertThat(send(Long.valueOf(roomB.id()), borrower).getSequence()).isPositive();
        assertThat(rooms.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("현재 동네 인증이 만료돼도 다른 저장 동네의 유효 소속으로 채팅 가능")
    void expiredCurrentCommunityDoesNotInvalidateOtherSavedCommunity() {
        CommunityMembership selected = memberships.findByMemberIdAndCommunityId(borrower, communityB).orElseThrow();
        selected.reverify(now.minusSeconds(86400L * 32));
        memberships.saveAndFlush(selected);
        assertThat(membershipQuery.findActiveCommunityId(borrower)).isEmpty();
        var room = view.open(itemA, borrower);
        assertThat(room.access().canSend()).isTrue();
        assertThat(send(Long.valueOf(room.id()), borrower).getSequence()).isEqualTo(1);
        assertCode(() -> view.open(itemB, borrower), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("저장 동네에서 이탈하면 그 방만 전송 차단하고 조회·다른 동네 채팅 유지")
    void leavingSavedCommunityRevokesOnlyItsChatPermission() {
        var roomA = view.open(itemA, borrower);
        var roomB = view.open(itemB, borrower);
        myCommunities.leave(borrower, communityA);
        var detail = view.get(Long.valueOf(roomA.id()), borrower);
        assertThat(detail.access().canSend()).isFalse();
        assertThat(detail.access().reason()).isEqualTo("COMMUNITY_CHANGED");
        assertThat(view.list(borrower, "ALL", 0, 20).items())
            .filteredOn(room -> room.id().equals(roomA.id()))
            .allSatisfy(room -> assertThat(room.access()).isEqualTo(detail.access()));
        assertCode(() -> send(Long.valueOf(roomA.id()), borrower), ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        assertCode(() -> view.open(itemA, borrower), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(send(Long.valueOf(roomB.id()), borrower).getSequence()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 동네로 선택돼 있어도 그 동네 인증이 만료되면 메시지 전송 금지")
    void selectedCommunityCannotBypassExpiredMembership() {
        var room = view.open(itemB, borrower);
        CommunityMembership selected = memberships.findByMemberIdAndCommunityId(borrower, communityB).orElseThrow();
        selected.reverify(now.minusSeconds(86400L * 32));
        memberships.saveAndFlush(selected);
        assertThat(view.get(Long.valueOf(room.id()), borrower).access().reason()).isEqualTo("COMMUNITY_CHANGED");
        assertCode(() -> send(Long.valueOf(room.id()), borrower), ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
        assertCode(() -> view.open(itemB, borrower), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("동네 전환 전후 알림·읽음 수는 수신자 기준 유지 및 타인 알림 비노출")
    void notificationsAndReadStateSurviveCommunitySwitch() {
        Long mine = notification(borrower, itemA);
        notification(owner, itemB);
        assertThat(notifications.list(borrower, null, 20, null).items()).hasSize(1);
        assertThat(notifications.unreadCount(borrower).unreadCount()).isEqualTo(1);
        myCommunities.select(borrower, communityA);
        notificationReads.markRead(borrower, mine);
        myCommunities.select(borrower, communityB);
        assertThat(notifications.list(borrower, null, 20, null).items()).hasSize(1);
        assertThat(notifications.unreadCount(borrower).unreadCount()).isZero();
    }

    @Test
    @DisplayName("현재 동네가 달라도 제재 전 승인 거래의 방 복구·정리 대화 허용, 소속 이탈 시 차단")
    void savedMembershipAndPreSanctionRentalExceptionWorkTogether() {
        Rental rental = Rental.request(itemA, owner, borrower, communityA,
            LocalDate.now(), LocalDate.now().plusDays(2), now.plusSeconds(3600),
            now.plusSeconds(86400L * 3), "기존 거래", 10L, "공용 장소");
        rental.approve(now.minusSeconds(120));
        em.persist(rental);
        Report report = Report.open(borrower, ReportTargetType.MEMBER, owner, null,
            communityA, ReportReasonCode.NO_SHOW, null, "{}");
        em.persist(report);
        ReportAction action = ReportAction.of(report.getId(), borrower, ReportActionType.BAN, "제재 검증", null);
        em.persist(action);
        em.persist(Sanction.from(owner, action.getId(), ReportActionType.BAN, now.minusSeconds(60)));
        em.flush();
        when(sanctions.isRestricted(owner)).thenReturn(true);
        when(sanctions.findRestrictedAmong(List.of(owner))).thenReturn(java.util.Set.of(owner));

        var response = view.open(itemA, borrower);
        Long roomId = Long.valueOf(response.id());
        assertThat(response.access().reason()).isEqualTo("EXISTING_RENTAL_ONLY");
        assertThat(response.access().allowedRentalIds()).containsExactly(rental.getId().toString());
        assertThat(view.list(borrower, "ALL", 0, 20).items().getFirst().access()).isEqualTo(response.access());
        for (Long member : List.of(owner, borrower)) {
            assertCode(() -> send(roomId, member), ErrorCode.MEMBER_RESTRICTED);
            assertThat(messages.send(roomId, member,
                new SendMessageRequest(UUID.randomUUID(), "기존 거래 정리", rental.getId().toString())).getSequence()).isPositive();
        }
        myCommunities.leave(borrower, communityA);
        assertCode(() -> messages.send(roomId, borrower,
            new SendMessageRequest(UUID.randomUUID(), "소속 이탈 후 전송", rental.getId().toString())),
            ErrorCode.COMMUNITY_VERIFICATION_REQUIRED);
    }

    private Long community(String name) {
        return communities.saveAndFlush(Community.of(name, "전북 전주시 " + name,
            new BigDecimal("35.8000000"), new BigDecimal("127.1000000"), 3000, CommunityStatus.ACTIVE)).getId();
    }

    private Long item(Long community) {
        return items.saveAndFlush(Item.create(owner, community,
            categories.findByActiveTrueOrderBySortOrderAscIdAsc().getFirst().getId(), 10L,
            "두 동네 물건", null, LocalDate.now(), LocalDate.now().plusDays(30), now)).getId();
    }

    private com.billim.domain.chat.entity.ChatMessage send(Long room, Long member) {
        return messages.send(room, member, new SendMessageRequest(UUID.randomUUID(), "동네 전환 검증", null));
    }

    private Long notification(Long member, Long target) {
        OutboxEvent event = OutboxEvent.pending(UUID.randomUUID().toString(), EventType.RENTAL_APPROVED,
            target, 0L, "{}", now);
        em.persist(event);
        em.flush();
        Notification notification = Notification.of(member, event.getId(), EventType.RENTAL_APPROVED,
            "동네 알림", NotificationTargetType.RENTAL, target);
        em.persist(notification);
        em.flush();
        return notification.getId();
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
