package com.billim.domain.notification.service;

import com.billim.domain.member.entity.Member;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.domain.notification.dto.NotificationResponse;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.entity.NotificationTargetType;
import com.billim.domain.notification.entity.OutboxEvent;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 알림 읽음 처리 (API 명세 D_050, D_051) */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({NotificationReadServiceDbTest.Fixtures.class, NotificationReadService.class, JpaAuditingConfig.class})
class NotificationReadServiceDbTest {

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
    private static final Instant EARLIER = Instant.parse("2026-10-09T01:00:00Z");

    @Autowired EntityManager em;
    @Autowired NotificationReadService readService;

    private long me;
    private long neighbor;
    /** 오래된 것부터 저장한 내 알림 ID */
    private List<Long> myNotificationIds;
    private long neighborNotificationId;
    private long aggregateSequence;

    @BeforeEach
    void setUp() {
        me = saveMember("소빈");
        neighbor = saveMember("정우");

        myNotificationIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            myNotificationIds.add(saveNotification(me, "알림 " + i));
        }
        neighborNotificationId = saveNotification(neighbor, "이웃의 알림");
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("내 알림을 읽으면 readAt이 기록된다")
    void markReadRecordsReadAt() {
        NotificationResponse response = readService.markRead(me, myNotificationIds.getFirst());

        assertThat(response.readAt()).isEqualTo(NOW);
        assertThat(find(myNotificationIds.getFirst()).getReadAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 읽은 알림을 다시 읽어도 최초 readAt을 유지한다")
    void repeatedReadKeepsFirstReadAt() {
        Notification notification = find(myNotificationIds.getFirst());
        notification.markRead(EARLIER);
        em.flush();
        em.clear();

        NotificationResponse response = readService.markRead(me, myNotificationIds.getFirst());

        assertThat(response.readAt()).isEqualTo(EARLIER);
    }

    @Test
    @DisplayName("타인의 알림은 존재를 알리지 않고 404다")
    void otherMembersNotificationIsNotFound() {
        assertThatThrownBy(() -> readService.markRead(me, neighborNotificationId))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));

        assertThat(find(neighborNotificationId).getReadAt()).isNull();
    }

    @Test
    @DisplayName("없는 알림도 404다")
    void missingNotificationIsNotFound() {
        assertThatThrownBy(() -> readService.markRead(me, 999_999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("모두 읽음은 기준 ID 이하만 처리하고 이후 신규 알림은 미열람으로 남긴다")
    void markAllReadStopsAtThroughId() {
        long throughId = myNotificationIds.get(1);

        int updated = readService.markAllRead(me, throughId);
        em.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(find(myNotificationIds.get(0)).getReadAt()).isEqualTo(NOW);
        assertThat(find(myNotificationIds.get(1)).getReadAt()).isEqualTo(NOW);
        // 기준 ID보다 큰(= 더 최근) 알림은 그대로 미열람
        assertThat(find(myNotificationIds.get(2)).getReadAt()).isNull();
        assertThat(find(myNotificationIds.get(3)).getReadAt()).isNull();
    }

    @Test
    @DisplayName("모두 읽음은 타인의 알림을 건드리지 않는다")
    void markAllReadTouchesOwnNotificationsOnly() {
        readService.markAllRead(me, Long.MAX_VALUE);
        em.clear();

        assertThat(find(neighborNotificationId).getReadAt()).isNull();
    }

    @Test
    @DisplayName("모두 읽음을 반복해도 결과가 같고 readAt이 바뀌지 않는다")
    void markAllReadIsRepeatable() {
        long throughId = myNotificationIds.getLast();
        readService.markAllRead(me, throughId);
        em.clear();

        int updatedAgain = readService.markAllRead(me, throughId);

        assertThat(updatedAgain).isZero();
        assertThat(find(myNotificationIds.getFirst()).getReadAt()).isEqualTo(NOW);
    }

    private Notification find(long notificationId) {
        return em.find(Notification.class, notificationId);
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
}
