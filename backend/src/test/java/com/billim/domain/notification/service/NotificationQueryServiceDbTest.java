package com.billim.domain.notification.service;

import com.billim.domain.member.entity.Member;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.domain.notification.dto.NotificationPageResponse;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.entity.NotificationTargetType;
import com.billim.domain.notification.entity.OutboxEvent;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 내 알림 목록·안 읽은 수 (API 명세 D_048, D_049) */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({NotificationQueryServiceDbTest.Fixtures.class, NotificationQueryService.class, JpaAuditingConfig.class})
class NotificationQueryServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    private static final Instant NOW = Instant.parse("2026-10-10T01:00:00Z");

    @Autowired EntityManager em;
    @Autowired NotificationQueryService queryService;

    private long me;
    private long neighbor;
    /** 오래된 것부터 저장한 내 알림 ID */
    private List<Long> myNotificationIds;
    private long aggregateSequence;

    @BeforeEach
    void setUp() {
        me = saveMember("소빈");
        neighbor = saveMember("정우");

        myNotificationIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            myNotificationIds.add(saveNotification(me, "알림 " + i));
        }
        // 타인의 알림. 어떤 조회에도 섞이면 안 된다
        saveNotification(neighbor, "이웃의 알림");
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("최신(id DESC)부터 내 알림만 내려준다")
    void listsOwnNotificationsNewestFirst() {
        NotificationPageResponse page = queryService.list(me, null, 20, null);

        assertThat(page.items()).hasSize(5);
        assertThat(page.items()).extracting("id")
                .containsExactly(idStrings(reversed(myNotificationIds)));
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("limit보다 많으면 hasMore와 nextCursor를 내려주고, 커서로 이어서 받는다")
    void pagesWithExclusiveCursor() {
        NotificationPageResponse first = queryService.list(me, null, 2, null);

        assertThat(first.hasMore()).isTrue();
        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isEqualTo(String.valueOf(myNotificationIds.get(3)));

        NotificationPageResponse second =
                queryService.list(me, Long.valueOf(first.nextCursor()), 2, null);

        // 커서는 exclusive라 직전 페이지의 마지막 ID는 다시 오지 않는다
        assertThat(second.items()).extracting("id")
                .containsExactly(String.valueOf(myNotificationIds.get(2)),
                        String.valueOf(myNotificationIds.get(1)));
    }

    @Test
    @DisplayName("read 필터로 읽음·미열람을 나눠 조회한다")
    void filtersByReadState() {
        markRead(myNotificationIds.get(0));
        markRead(myNotificationIds.get(1));
        em.flush();
        em.clear();

        assertThat(queryService.list(me, null, 20, true).items()).hasSize(2);
        assertThat(queryService.list(me, null, 20, false).items()).hasSize(3);
        assertThat(queryService.list(me, null, 20, null).items()).hasSize(5);
    }

    @Test
    @DisplayName("안 읽은 수는 본인의 readAt 없는 알림만 센다")
    void countsOwnUnreadOnly() {
        markRead(myNotificationIds.get(0));
        em.flush();
        em.clear();

        assertThat(queryService.unreadCount(me).unreadCount()).isEqualTo(4);
        assertThat(queryService.unreadCount(neighbor).unreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("알림이 없는 회원은 빈 목록과 0을 받는다")
    void emptyResultForMemberWithoutNotifications() {
        long stranger = saveMember("지민");
        em.flush();

        assertThat(queryService.list(stranger, null, 20, null).items()).isEmpty();
        assertThat(queryService.unreadCount(stranger).unreadCount()).isZero();
    }

    private long saveMember(String name) {
        Member member = Member.register(name);
        em.persist(member);
        return member.getId();
    }

    private long saveNotification(long recipientId, String title) {
        long aggregateId = ++aggregateSequence;
        OutboxEvent event = OutboxEvent.pending(
                "rental:%d:version:0:approved".formatted(aggregateId),
                EventType.RENTAL_APPROVED, aggregateId, 0L, "{}", NOW);
        em.persist(event);
        em.flush();

        Notification notification = Notification.of(recipientId, event.getId(),
                EventType.RENTAL_APPROVED, title, NotificationTargetType.RENTAL, aggregateId);
        em.persist(notification);
        em.flush();
        return notification.getId();
    }

    private void markRead(long notificationId) {
        em.find(Notification.class, notificationId).markRead(NOW);
    }

    private static List<Long> reversed(List<Long> ids) {
        List<Long> copy = new ArrayList<>(ids);
        copy.sort((left, right) -> Long.compare(right, left));
        return copy;
    }

    private static String[] idStrings(List<Long> ids) {
        return ids.stream().map(String::valueOf).toArray(String[]::new);
    }
}
