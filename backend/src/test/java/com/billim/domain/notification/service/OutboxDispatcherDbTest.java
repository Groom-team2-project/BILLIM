package com.billim.domain.notification.service;

import com.billim.domain.member.entity.Member;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.entity.OutboxStatus;
import com.billim.domain.notification.repository.NotificationRepository;
import com.billim.domain.notification.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사건 발행 → 소비 → 알림 생성의 통합 동작.
 * 명세의 정합성 기준을 그대로 검증한다: 롤백한 거래의 알림은 없고, 사건을 다시 소비해도 알림이 늘지 않는다.
 * (API 명세 15장, ERD 4.6)
 *
 * <p>커밋 후 동작을 확인해야 하므로 테스트를 트랜잭션으로 감싸지 않고 직접 정리한다.
 * 주기 실행은 끄고 디스패처를 테스트가 직접 호출한다.
 */
@Import(OutboxDispatcherDbTest.MySql.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.notification.outbox.enabled=false"
})
class OutboxDispatcherDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    private static final long RENTAL_ID = 501L;

    @Autowired DomainEventPublisher publisher;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired OutboxEventStore store;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbc;

    private long owner;
    private long borrower;

    @BeforeEach
    void setUp() {
        clean();
        owner = memberRepository.save(Member.register("정우")).getId();
        borrower = memberRepository.save(Member.register("지민")).getId();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    /** 자식 → 부모 순서로 지운다 */
    private void clean() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM event_deliveries");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM members");
    }

    @Test
    @DisplayName("사건을 소비하면 수신자마다 알림이 생기고 사건은 완료 처리된다")
    void dispatchCreatesNotificationPerRecipient() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER), recipient(owner, RecipientRole.OWNER))));

        int completed = dispatcher.dispatchBatch();

        assertThat(completed).isEqualTo(1);
        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(borrower)).isEqualTo(1);
        assertThat(onlyEvent().getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    @DisplayName("알림 문구는 수신자 역할에 맞게 생성된다")
    void notificationTitleFollowsRecipientRole() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_REQUESTED, RENTAL_ID, 0L,
                payload(recipient(owner, RecipientRole.OWNER))));

        dispatcher.dispatchBatch();

        Notification notification = notificationRepository.findAll().getFirst();
        assertThat(notification.getRecipientId()).isEqualTo(owner);
        assertThat(notification.getTitle()).isEqualTo("정우 님이 전동드릴 대여를 요청했어요");
        assertThat(notification.getTargetId()).isEqualTo(RENTAL_ID);
    }

    @Test
    @DisplayName("이미 소비한 사건을 다시 소비해도 알림이 늘지 않는다")
    void reconsumingEventDoesNotDuplicateNotifications() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER))));
        dispatcher.dispatchBatch();
        assertThat(notificationRepository.findAll()).hasSize(1);

        // 작업자 장애로 완료 기록만 유실된 상황
        jdbc.update("UPDATE outbox_events SET status = 'PENDING', lease_until = NULL, processed_at = NULL");

        dispatcher.dispatchBatch();

        assertThat(notificationRepository.findAll()).hasSize(1);
        assertThat(onlyEvent().getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    @DisplayName("완료된 사건은 다음 실행에서 다시 점유하지 않는다")
    void completedEventIsNotClaimedAgain() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER))));
        dispatcher.dispatchBatch();

        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("같은 상태 변경을 두 번 발행해도 사건은 한 건, 알림도 한 건이다")
    void samePublishTwiceKeepsSingleEvent() {
        DomainEvent event = DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER)));

        publishInTransaction(event);
        publishInTransaction(event);
        dispatcher.dispatchBatch();

        assertThat(outboxEventRepository.findAll()).hasSize(1);
        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("상태 변경이 롤백되면 사건도 알림도 남지 않는다")
    void rolledBackTransactionLeavesNoEvent() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            publisher.publish(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                    payload(recipient(borrower, RecipientRole.BORROWER))));
            throw new IllegalStateException("거래 처리 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outboxEventRepository.findAll()).isEmpty();
        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션 없이 발행하면 거부한다. 상태 변경과 따로 커밋되지 않게 막는다")
    void publishRequiresCallerTransaction() {
        assertThatThrownBy(() -> publisher.publish(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER)))))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(outboxEventRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("수신자가 없는 사건은 알림 없이 완료 처리된다")
    void eventWithoutRecipientsCompletesWithoutNotification() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_EXPIRED, RENTAL_ID, 3L, payload()));

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);
        assertThat(notificationRepository.findAll()).isEmpty();
        assertThat(onlyEvent().getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    @Test
    @DisplayName("사건 적재 시점에는 알림이 없고, 소비 후에 생긴다")
    void notificationIsCreatedAfterCommitNotDuringPublish() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER))));

        assertThat(notificationRepository.findAll()).isEmpty();
        assertThat(onlyEvent().getStatus()).isEqualTo(OutboxStatus.PENDING);

        dispatcher.dispatchBatch();

        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("다른 작업자가 점유 중인 사건은 가져가지 않는다")
    void leasedEventIsNotClaimableUntilLeaseExpires() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER))));

        leaseUntil("UTC_TIMESTAMP(6) + INTERVAL 10 MINUTE");

        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("점유가 만료된 사건은 다시 가져가 처리한다")
    void expiredLeaseIsReclaimed() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER))));

        // 작업자가 처리 중 죽어 PROCESSING으로 남고 점유가 만료된 상황
        leaseUntil("UTC_TIMESTAMP(6) - INTERVAL 10 MINUTE");

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);
        assertThat(notificationRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("재선점된 사건은 이전 작업자의 완료와 실패 기록을 무시한다")
    void staleWorkerCannotOverwriteReclaimedEvent() {
        publishInTransaction(DomainEvent.of(EventType.RENTAL_APPROVED, RENTAL_ID, 2L,
                payload(recipient(borrower, RecipientRole.BORROWER))));

        OutboxEventStore.ClaimedEvent first = store.claim(Instant.now(), 1, Duration.ofMinutes(1)).getFirst();
        jdbc.update("UPDATE outbox_events SET lease_until = UTC_TIMESTAMP(6) - INTERVAL 10 MINUTE WHERE id = ?",
                first.eventId());
        OutboxEventStore.ClaimedEvent second = store.claim(Instant.now(), 1, Duration.ofMinutes(1)).getFirst();

        assertThat(second.attempt()).isEqualTo(first.attempt() + 1);
        assertThat(store.complete(first, Instant.now())).isFalse();
        assertThat(store.fail(first, Instant.now(), Duration.ofSeconds(10), 5)).isFalse();
        assertThat(onlyEvent().getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(onlyEvent().getAttempts()).isEqualTo(second.attempt());

        assertThat(store.complete(second, Instant.now())).isTrue();
        assertThat(onlyEvent().getStatus()).isEqualTo(OutboxStatus.DONE);
    }

    /**
     * 점유 상태를 직접 만든다. DATETIME은 UTC instant를 담으므로(ERD 1장) 시각도 UTC로 계산한다.
     * Java Timestamp를 바인딩하면 JDBC가 JVM 기본 시간대로 변환해 기준이 어긋난다.
     */
    private void leaseUntil(String utcExpression) {
        jdbc.update("UPDATE outbox_events SET status = 'PROCESSING', lease_until = " + utcExpression);
    }

    private OutboxEvent onlyEvent() {
        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    private void publishInTransaction(DomainEvent event) {
        transactionTemplate.executeWithoutResult(status -> publisher.publish(event));
    }

    private static EventPayload.Recipient recipient(long memberId, RecipientRole role) {
        return EventPayload.Recipient.of(memberId, role);
    }

    private static EventPayload payload(EventPayload.Recipient... recipients) {
        return EventPayload.forRental(RENTAL_ID, List.of(recipients),
                EventPayload.Display.of("정우", "전동드릴"));
    }
}
