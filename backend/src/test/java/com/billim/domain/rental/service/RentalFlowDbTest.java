package com.billim.domain.rental.service;

import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.member.entity.MemberRole;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.security.LoginMember;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.billim.domain.item.repository.ItemRepository;
import org.testcontainers.mysql.MySQLContainer;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import({RentalFlowDbTest.Config.class, JpaAuditingConfig.class, RentalService.class, RentalQueryService.class, AppointmentService.class, AppointmentCommandService.class, RentalRequestEffects.class,
        com.billim.domain.notification.service.OutboxEventPublisher.class, com.billim.global.json.JsonCodec.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RentalFlowDbTest {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final LoginMember OWNER = new LoginMember(9101L, MemberRole.USER);
    private static final LoginMember BORROWER = new LoginMember(9102L, MemberRole.USER);
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean @ServiceConnection MySQLContainer mysql() { return new MySQLContainer("mysql:8.4"); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.billim.global.repository.IdempotencyRecordRepository records;
    @MockitoBean RentalRequestPolicy policy;
    @Autowired RentalService service;
    @Autowired RentalQueryService queries;
    @Autowired AppointmentCommandService appointments;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ItemRepository items;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO members (id,display_name,created_at,updated_at) VALUES (9101,'소유자',NOW(6),NOW(6)), (9102,'요청자',NOW(6),NOW(6))");
        jdbc.update("""
                INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,
                  available_start_date,available_end_date,visibility,created_at,updated_at)
                SELECT 9101,9101,1,id,1,'드릴','2026-10-01','2026-11-30','PUBLIC',NOW(6),NOW(6)
                FROM categories ORDER BY id LIMIT 1
                """);
        insertRental(9101, "2026-10-10", "2026-10-11", "REQUESTED");
        insertRental(9102, "2026-10-10", "2026-10-12", "REQUESTED");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM idempotency_records WHERE member_id IN (9101,9102)");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM rental_status_histories WHERE rental_id IN (SELECT id FROM rentals WHERE item_id=9101)");
        jdbc.update("UPDATE appointment_slots SET confirmed_proposal_id = NULL WHERE rental_id IN (SELECT id FROM rentals WHERE item_id=9101)");
        jdbc.update("DELETE FROM appointment_proposals WHERE slot_id IN (SELECT id FROM appointment_slots WHERE rental_id IN (SELECT id FROM rentals WHERE item_id=9101))");
        jdbc.update("DELETE FROM appointment_slots WHERE rental_id IN (SELECT id FROM rentals WHERE item_id=9101)");
        jdbc.update("DELETE FROM rentals WHERE item_id = 9101");
        jdbc.update("DELETE FROM community_places WHERE community_id=9101");
        jdbc.update("DELETE FROM communities WHERE id=9101");
        jdbc.update("DELETE FROM chat_rooms WHERE item_id=9101");
        jdbc.update("DELETE FROM items WHERE id = 9101");
        jdbc.update("DELETE FROM members WHERE id IN (9101,9102)");
    }

    @Test
    @DisplayName("실제 DB 승인·전달·반납 및 버전별 이력 저장")
    void approveHandoverReturn() {
        var approved = service.approveRental(9101L, key(), new RentalVersionRequest(0L), OWNER);
        assertThat(approved.rental().status()).isEqualTo(RentalStatus.APPROVED);
        assertThat(approved.rental().version()).isEqualTo(1);
        var active = service.handoverRental(9101L, key(), new RentalVersionRequest(1L), OWNER);
        assertThat(active.rental().status()).isEqualTo(RentalStatus.ACTIVE);
        var returned = service.returnRental(9101L, key(), new RentalVersionRequest(2L), OWNER);
        assertThat(returned.rental().version()).isEqualTo(3);
        assertThat(returned.history()).extracting(RentalDetailStatusResponse.History::toStatus)
                .containsExactly(RentalStatus.REQUESTED, RentalStatus.APPROVED, RentalStatus.ACTIVE, RentalStatus.RETURNED);
        assertThat(returned.returnedAt()).isEqualTo(NOW);
        assertThat(jdbc.queryForObject("SELECT status FROM rentals WHERE id=9102", String.class)).isEqualTo("REQUESTED");
    }

    @Test
    @DisplayName("겹치는 요청 동시 승인 시 한 건만 승인")
    void concurrentApprovalsHaveOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> approveAfter(start, 9101L));
            var second = executor.submit(() -> approveAfter(start, 9102L));
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("APPROVED", "RENTAL_DATE_CONFLICT");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rentals WHERE item_id=9101 AND status='APPROVED'", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 회원의 승인·취소 경합은 한 건만 성공하고 다른 명령은 버전 충돌")
    void approvalAndCancellationUseSameItemLock() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var approval = executor.submit(() -> approveAfter(start, 9101L));
            var cancellation = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    return service.cancelRental(9101L, key(), new CancelRentalRequest(0L, "일정 변경"), BORROWER)
                            .rental().status().name();
                } catch (BusinessException e) {
                    return e.getErrorCode().name();
                }
            });
            start.countDown();
            var results = List.of(approval.get(20, TimeUnit.SECONDS), cancellation.get(20, TimeUnit.SECONDS));
            assertThat(results).contains("VERSION_CONFLICT");
            assertThat(results.stream().filter(r -> r.equals("APPROVED") || r.equals("CANCELED"))).hasSize(1);
        }
        assertThat(jdbc.queryForObject("SELECT version FROM rentals WHERE id=9101", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("물건 잠금을 기다린 승인은 먼저 커밋된 가능 기간 변경을 반영")
    void approvalSeesPeriodUpdatedWhileWaiting() throws Exception {
        var locked = new CountDownLatch(1);
        var approving = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                items.findByIdForUpdate(9101L).orElseThrow();
                jdbc.update("UPDATE items SET available_start_date='2026-10-11' WHERE id=9101");
                locked.countDown();
                try {
                    if (!approving.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("승인 시작 대기 초과");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            var approval = executor.submit(() -> {
                if (!locked.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("물건 잠금 대기 초과");
                approving.countDown();
                return approveAfter(new CountDownLatch(0), 9101L);
            });
            update.get(20, TimeUnit.SECONDS);
            assertThat(approval.get(20, TimeUnit.SECONDS)).isEqualTo("INVALID_REQUEST");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM rentals WHERE id=9101", String.class)).isEqualTo("REQUESTED");
    }

    @Test
    @DisplayName("후속 저장 실패 시 거래 변경·버전·이력 모두 롤백")
    void effectFailureRollsBackMutation() {
        doThrow(new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE)).when(records).saveAndFlush(any());
        assertThatThrownBy(() -> service.approveRental(9101L, key(), new RentalVersionRequest(0L), OWNER))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM rentals WHERE id=9101", String.class)).isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject("SELECT version FROM rentals WHERE id=9101", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental_status_histories WHERE rental_id=9101", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("승인 취소 후 점유 해제, 반납 완료는 약정 기간 점유 유지")
    void canceledDatesReleaseButReturnedDatesRemainOccupied() {
        service.approveRental(9101L, key(), new RentalVersionRequest(0L), OWNER);
        service.cancelRental(9101L, key(), new CancelRentalRequest(1L, "일정 변경"), BORROWER);
        service.approveRental(9102L, key(), new RentalVersionRequest(0L), OWNER);
        service.handoverRental(9102L, key(), new RentalVersionRequest(1L), OWNER);
        service.returnRental(9102L, key(), new RentalVersionRequest(2L), OWNER);
        var calendar = queries.getAvailability(9101L, LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-13"),
                LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-12"), BORROWER);
        assertThat(calendar.selectedRangeAvailable()).isFalse();
        assertThat(calendar.occupiedRanges()).containsExactly(new RentalAvailabilityResponse.DateRange(
                LocalDate.parse("2026-10-10"), LocalDate.parse("2026-10-12")));
    }

    @Test
    @DisplayName("실제 목록 필터·안정 정렬·역할별 집계·예정 3건")
    void queryFiltersCountsAndUpcoming() {
        jdbc.update("UPDATE rentals SET status='ACTIVE', start_date='2026-10-08', end_date='2026-10-09', due_at='2026-10-09 15:00:00' WHERE id=9101");
        jdbc.update("UPDATE rentals SET status='RETURNED' WHERE id=9102");
        insertRental(9103, "2026-10-12", "2026-10-13", "APPROVED");
        insertRental(9104, "2026-10-11", "2026-10-12", "APPROVED");
        insertRental(9105, "2026-10-10", "2026-10-11", "APPROVED");
        var overdue = queries.getRentalList(RentalRole.BORROWER, null, true, 9101L, 0, 20, BORROWER);
        assertThat(overdue.items()).extracting(RentalSummaryResponse::id).containsExactly("9101");
        assertThat(queries.getRentalList(RentalRole.OWNER, null, false, null, 0, 20, BORROWER).items()).isEmpty();
        var first = queries.getRentalList(RentalRole.BORROWER, RentalStatus.APPROVED, false, null, 0, 2, BORROWER);
        assertThat(first.items()).extracting(RentalSummaryResponse::id).containsExactly("9105", "9104");
        assertThat(first.hasNext()).isTrue();
        var counts = queries.getRentalCountStatus(BORROWER);
        assertThat(counts.borrowed().active()).isEqualTo(1);
        assertThat(counts.borrowed().overdue()).isEqualTo(1);
        assertThat(counts.borrowed().returned()).isEqualTo(1);
        assertThat(counts.borrowed().approved()).isEqualTo(3);
        assertThat(counts.lent().active()).isZero();
        assertThat(counts.nextRentals()).extracting(RentalSummaryResponse::id).containsExactly("9105", "9104", "9103");
    }

    @Test
    @DisplayName("확정 RETURN 제안만 반납 약속 스냅샷으로 저장")
    void returnUsesConfirmedProposal() {
        service.approveRental(9101L, key(), new RentalVersionRequest(0L), OWNER);
        service.handoverRental(9101L, key(), new RentalVersionRequest(1L), OWNER);
        jdbc.update("DELETE FROM appointment_slots WHERE rental_id=9101 AND kind='RETURN'");
        jdbc.update("INSERT INTO appointment_slots (id,rental_id,kind,created_at,updated_at) VALUES (9101,9101,'RETURN',NOW(6),NOW(6))");
        jdbc.update("""
                INSERT INTO appointment_proposals (id,slot_id,proposer_id,place_id,scheduled_at,place_name_snapshot,status,
                  accepted_by,accepted_at,created_at,updated_at)
                VALUES (9101,9101,9101,1,'2026-10-09 15:00:00','정문','CONFIRMED',9102,NOW(6),NOW(6),NOW(6))
                """);
        jdbc.update("UPDATE appointment_slots SET confirmed_proposal_id=9101 WHERE id=9101");
        service.returnRental(9101L, key(), new RentalVersionRequest(2L), OWNER);
        assertThat(jdbc.queryForObject("SELECT return_promise_kept FROM rentals WHERE id=9101", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT return_promise_at FROM rentals WHERE id=9101", LocalDateTime.class).toInstant(ZoneOffset.UTC)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("재전송은 이후 상태 변경에도 최초 응답 재사용, 본문 변경은 충돌")
    void replaysOriginalSnapshotAndRejectsChangedBody() {
        String key = key();
        var first = service.approveRental(9101L, key, new RentalVersionRequest(0L), OWNER);
        service.handoverRental(9101L, key(), new RentalVersionRequest(1L), OWNER);
        assertThat(service.approveRental(9101L, key, new RentalVersionRequest(0L), OWNER)).isEqualTo(first);
        assertThatThrownBy(() -> service.approveRental(9101L, key, new RentalVersionRequest(1L), OWNER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("동일 요청 동시 재전송은 상태·이력·사건 각각 한 번 저장")
    void concurrentRetryHasOneEffect() throws Exception {
        String key = key();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<RentalDetailStatusResponse> request = () -> {
                start.await(10, TimeUnit.SECONDS);
                return service.rejectRental(9101L, key, new RejectRentalRequest(0L, "일정 변경"), OWNER);
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rental_status_histories WHERE rental_id=9101", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("성공 기록은 24시간 유효, 만료 경계에서는 신규 명령 상태 검사")
    void expiryBoundaryUsesBusinessGuards() {
        String key = key();
        service.rejectRental(9101L, key, new RejectRentalRequest(0L, "일정 변경"), OWNER);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM idempotency_records", LocalDateTime.class))
                .isEqualTo(LocalDateTime.ofInstant(NOW.plus(Duration.ofHours(24)), ZoneOffset.UTC));
        jdbc.update("UPDATE idempotency_records SET expires_at=?", LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> service.rejectRental(9101L, key, new RejectRentalRequest(0L, "일정 변경"), OWNER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("요청 생성 성공 응답 재사용과 상대방 알림 사건 저장")
    void createReplayAndRecipient() {
        when(policy.requireRequestAllowed(any(), anyLong())).thenReturn("정문");
        String key = key();
        var request = new CreateRentalRequest("9101", LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-21"));
        var first = service.createRental(key, request, BORROWER);
        assertThat(service.createRental(key, request, BORROWER)).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT http_status FROM idempotency_records", Integer.class)).isEqualTo(201);
        var payload = new com.billim.global.json.JsonCodec().read(
                jdbc.queryForObject("SELECT payload FROM outbox_events", String.class),
                com.billim.domain.notification.service.EventPayload.class);
        assertThat(payload.recipients()).containsExactly(
                com.billim.domain.notification.service.EventPayload.Recipient.of(9101L,
                        com.billim.domain.notification.service.RecipientRole.OWNER));
        assertThat(payload.display().actorName()).isEqualTo("요청자");
        assertThat(payload.display().label()).isNull();
    }

    @Test
    @DisplayName("같은 키도 경로·회원별 독립 처리 및 행동자 제외 사건 저장")
    void keysAreScopedToOperationAndMember() {
        String key = key();
        service.approveRental(9101L, key, new RentalVersionRequest(0L), OWNER);
        service.cancelRental(9101L, key, new CancelRentalRequest(1L, "일정 변경"), BORROWER);
        service.rejectRental(9102L, key, new RejectRentalRequest(0L, "일정 변경"), OWNER);
        assertThat(jdbc.queryForList("SELECT event_type FROM outbox_events ORDER BY id", String.class))
                .containsExactly("RENTAL_APPROVED", "RENTAL_CANCELED", "RENTAL_REJECTED");
        var codec = new com.billim.global.json.JsonCodec();
        var recipients = jdbc.queryForList("SELECT payload FROM outbox_events ORDER BY id", String.class)
                .stream().map(body -> codec.read(body, com.billim.domain.notification.service.EventPayload.class))
                .map(payload -> payload.recipients().getFirst().memberId()).toList();
        assertThat(recipients).containsExactly(9102L, 9101L, 9102L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("실패 요청은 기록하지 않으며 같은 키로 정상 재요청 가능")
    void failureDoesNotConsumeKey() {
        String key = key();
        assertThatThrownBy(() -> service.handoverRental(9101L, key, new RentalVersionRequest(0L), OWNER))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records", Integer.class)).isZero();
        service.approveRental(9101L, key(), new RentalVersionRequest(0L), OWNER);
        service.handoverRental(9101L, key, new RentalVersionRequest(1L), OWNER);
        service.returnRental(9101L, key, new RentalVersionRequest(2L), OWNER);
        assertThat(jdbc.queryForList("SELECT event_type FROM outbox_events ORDER BY id", String.class))
                .containsExactly("RENTAL_APPROVED", "RENTAL_HANDED_OVER", "RENTAL_RETURNED");
    }

    private String approveAfter(CountDownLatch start, long id) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            return service.approveRental(id, key(), new RentalVersionRequest(0L), OWNER).rental().status().name();
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        }
    }

    private String key() { return UUID.randomUUID().toString(); }

    @Test
    @DisplayName("약속 제안·수락·변경·철회가 슬롯 버전과 기존 확정을 보존하고 사건 저장")
    void appointmentLifecycle() {
        prepareAppointments();
        var proposed = appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                new ProposeAppointmentRequest(0L, NOW.plusSeconds(3600), "9101"), BORROWER);
        assertThat(proposed.version()).isEqualTo(1);
        long first = Long.parseLong(proposed.pendingProposal().id());
        var confirmed = appointments.accept(first, new AppointmentVersionRequest(1L), OWNER);
        assertThat(confirmed.version()).isEqualTo(2);
        assertThat(confirmed.confirmedProposal().id()).isEqualTo(Long.toString(first));
        assertThat(confirmed.pendingProposal()).isNull();
        var changed = appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                new ProposeAppointmentRequest(2L, NOW.plusSeconds(7200), "9101"), OWNER);
        assertThat(changed.confirmedProposal().id()).isEqualTo(Long.toString(first));
        long second = Long.parseLong(changed.pendingProposal().id());
        var withdrawn = appointments.withdraw(second, new AppointmentVersionRequest(3L), OWNER);
        assertThat(withdrawn.version()).isEqualTo(4);
        assertThat(withdrawn.pendingProposal()).isNull();
        assertThat(withdrawn.confirmedProposal().id()).isEqualTo(Long.toString(first));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_type='APPOINTMENT_SLOT'", Long.class)).isEqualTo(4);
        assertThat(queries.getRentalDetailStatus(9101L, OWNER).appointments()).hasSize(2);
    }

    @Test
    @DisplayName("제안 교체와 재확정 시 이전 제안은 SUPERSEDED 처리")
    void replacesPendingAndConfirmed() {
        prepareAppointments();
        var first = appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                new ProposeAppointmentRequest(0L, NOW.plusSeconds(3600), "9101"), BORROWER);
        var second = appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                new ProposeAppointmentRequest(1L, NOW.plusSeconds(7200), "9101"), BORROWER);
        assertThat(jdbc.queryForObject("SELECT status FROM appointment_proposals WHERE id=?", String.class,
                first.pendingProposal().id())).isEqualTo("SUPERSEDED");
        appointments.accept(Long.parseLong(second.pendingProposal().id()), new AppointmentVersionRequest(2L), OWNER);
        var third = appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                new ProposeAppointmentRequest(3L, NOW.plusSeconds(10800), "9101"), OWNER);
        appointments.accept(Long.parseLong(third.pendingProposal().id()), new AppointmentVersionRequest(4L), BORROWER);
        assertThat(jdbc.queryForObject("SELECT status FROM appointment_proposals WHERE id=?", String.class,
                second.pendingProposal().id())).isEqualTo("SUPERSEDED");
    }

    @Test
    @DisplayName("자기 제안 수락·상대 제안 철회·과거 시각·다른 KST 날짜·비활성 장소 거부")
    void rejectsInvalidAppointmentCommands() {
        prepareAppointments();
        var proposed = appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                new ProposeAppointmentRequest(0L, NOW.plusSeconds(3600), "9101"), BORROWER);
        long id = Long.parseLong(proposed.pendingProposal().id());
        assertThatThrownBy(() -> appointments.accept(id, new AppointmentVersionRequest(1L), BORROWER))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> appointments.withdraw(id, new AppointmentVersionRequest(1L), OWNER))
                .isInstanceOf(BusinessException.class);
        for (Instant at : List.of(NOW, NOW.plus(Duration.ofDays(1)))) {
            assertThatThrownBy(() -> appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                    new ProposeAppointmentRequest(1L, at, "9101"), BORROWER)).isInstanceOf(BusinessException.class);
        }
        jdbc.update("UPDATE community_places SET active=false WHERE id=9101");
        assertThatThrownBy(() -> appointments.accept(id, new AppointmentVersionRequest(1L), OWNER))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM appointment_proposals WHERE id=?", String.class, id)).isEqualTo("PROPOSED");
    }

    @Test
    @DisplayName("같은 슬롯 버전의 동시 제안은 한 건만 성공")
    void concurrentAppointmentProposals() throws Exception {
        prepareAppointments();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> task = () -> {
            start.await();
            try {
                appointments.propose(9101L, com.billim.domain.rental.entity.AppointmentKind.PICKUP,
                        new ProposeAppointmentRequest(0L, NOW.plusSeconds(3600), "9101"), BORROWER);
                return true;
            } catch (com.billim.global.exception.VersionConflictException e) { return false; }
        };
        try {
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
    }

    @Test
    @DisplayName("기존 채팅방이 있으면 신규 대여에 연결하고 최초 버전·이력 유지")
    void createLinksExistingChatRoom() {
        jdbc.update("INSERT INTO chat_rooms (id,item_id,owner_id,requester_id,created_at,updated_at) VALUES (9101,9101,9101,9102,NOW(6),NOW(6))");
        when(policy.requireRequestAllowed(any(), anyLong())).thenReturn("정문");
        var result = service.createRental(key(), new CreateRentalRequest("9101",
                LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-21")), BORROWER);
        assertThat(result.chatRoomId()).isEqualTo("9101");
        assertThat(result.rental().version()).isZero();
        assertThat(result.history()).singleElement().satisfies(h -> assertThat(h.rentalVersion()).isZero());
        assertThat(jdbc.queryForObject("SELECT chat_room_id FROM rentals WHERE id=?", Long.class,
                result.rental().id())).isEqualTo(9101);
    }

    private void prepareAppointments() {
        service.approveRental(9101L, key(), new RentalVersionRequest(0L), OWNER);
        jdbc.update("INSERT INTO communities (id,name,display_address,center_latitude,center_longitude,verification_radius_m,created_at,updated_at) VALUES (9101,'테스트 동네','공용 주소',37,127,500,NOW(6),NOW(6))");
        jdbc.update("INSERT INTO community_places (id,community_id,name,latitude,longitude,active,created_at,updated_at) VALUES (9101,9101,'정문',37,127,true,NOW(6),NOW(6))");
        jdbc.update("UPDATE rentals SET community_id=9101 WHERE id=9101");
    }

    private void insertRental(long id, String start, String end, String status) {
        jdbc.update("""
                INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,
                  request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,created_at,updated_at)
                VALUES (?,9101,9101,9102,1,?,?,?, ?,?,'드릴','정문',1,'2026-10-09 00:00:00','2026-10-09 00:00:00')
                """, id, start, end, status,
                LocalDateTime.ofInstant(LocalDate.parse(start).plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneOffset.UTC),
                LocalDateTime.ofInstant(LocalDate.parse(end).plusDays(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneOffset.UTC));
        jdbc.update("INSERT INTO rental_status_histories (rental_id,actor_id,to_status,rental_version,created_at,updated_at) VALUES (?,9102,?,0,NOW(6),NOW(6))", id, status);
    }
}
