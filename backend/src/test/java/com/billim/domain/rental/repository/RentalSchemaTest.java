package com.billim.domain.rental.repository;

import com.billim.domain.rental.entity.AppointmentProposal;
import com.billim.domain.rental.entity.AppointmentSlot;
import com.billim.domain.rental.entity.Rental;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.domain.rental.entity.RentalStatusHistory;
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
import org.springframework.dao.DataAccessException;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(RentalSchemaTest.MySql.class)
class RentalSchemaTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    @Autowired RentalRepository rentals;
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO members (id, display_name, created_at, updated_at)
                VALUES (9001, '소유자', NOW(6), NOW(6)), (9002, '요청자', NOW(6), NOW(6))
                """);
        jdbc.update("""
                INSERT INTO items (id, owner_id, community_id, category_id, place_id, title,
                    available_start_date, available_end_date, visibility, created_at, updated_at)
                SELECT 9001, 9001, 1, id, 1, '드릴', '2026-10-01', '2026-10-31', 'PUBLIC', NOW(6), NOW(6)
                FROM categories ORDER BY id LIMIT 1
                """);
        jdbc.update("""
                INSERT INTO rentals (id, item_id, owner_id, borrower_id, community_id,
                    start_date, end_date, status, request_expires_at, due_at,
                    item_title_snapshot, place_name_snapshot, place_id, created_at, updated_at)
                VALUES (9001, 9001, 9001, 9002, 1, '2026-10-10', '2026-10-11', 'REQUESTED',
                    '2026-10-10 15:00:00.123456', '2026-10-11 15:00:00', '드릴', '정문', 1, NOW(6), NOW(6))
                """);
    }

    @Test
    @DisplayName("마이그레이션 테이블에서 엔티티 조회 및 NULL·버전·마이크로초 보존")
    void rentalMappingMatchesMigration() {
        Rental rental = em.find(Rental.class, 9001L);
        assertThat(rental.getStatus()).isEqualTo(RentalStatus.REQUESTED);
        assertThat(rental.getVersion()).isZero();
        assertThat(rental.getReturnPromiseKept()).isNull();
        assertThat(rental.getRequestExpiresAt()).isEqualTo(Instant.parse("2026-10-10T15:00:00.123456Z"));
    }

    @Test
    @DisplayName("잘못된 날짜·동일 당사자·상태·외래 키 거부")
    void rentalConstraintsRejectInvalidChanges() {
        for (String assignment : new String[]{"end_date = '2026-10-09'", "borrower_id = owner_id",
                "status = 'UNKNOWN'", "item_id = 999999", "borrower_id = 999999", "chat_room_id = 999999"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE rentals SET " + assignment + " WHERE id = 9001"))
                    .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("생성 이력의 NULL 허용 및 거래 버전별 이력 중복 거부")
    void historyAllowsNullAndRejectsDuplicateVersion() {
        jdbc.update("""
                INSERT INTO rental_status_histories
                    (id, rental_id, to_status, rental_version, created_at, updated_at)
                VALUES (9001, 9001, 'REQUESTED', 0, NOW(6), NOW(6))
                """);
        RentalStatusHistory history = em.find(RentalStatusHistory.class, 9001L);
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getActorId()).isNull();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO rental_status_histories
                    (rental_id, to_status, rental_version, created_at, updated_at)
                VALUES (9001, 'APPROVED', 0, NOW(6), NOW(6))
                """)).isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("빈 슬롯 생성 후 제안을 저장하고 확정 참조 연결")
    void circularReferenceCanBeEstablishedInOrder() {
        createSlot();
        jdbc.update("""
                INSERT INTO appointment_proposals (id, slot_id, proposer_id, place_id, scheduled_at,
                    place_name_snapshot, status, created_at, updated_at)
                VALUES (9001, 9001, 9001, 1, '2026-10-10 01:00:00', '정문', 'PROPOSED', NOW(6), NOW(6))
                """);
        assertThat(em.find(AppointmentProposal.class, 9001L).getAcceptedBy()).isNull();
        jdbc.update("UPDATE appointment_proposals SET status = 'CONFIRMED', accepted_by = 9002, accepted_at = NOW(6) WHERE id = 9001");
        jdbc.update("UPDATE appointment_slots SET confirmed_proposal_id = 9001 WHERE id = 9001");
        assertThat(em.find(AppointmentSlot.class, 9001L).getConfirmedProposalId()).isEqualTo(9001L);
        assertThatThrownBy(() -> jdbc.update("UPDATE appointment_proposals SET accepted_by = proposer_id WHERE id = 9001"))
                .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE appointment_slots SET confirmed_proposal_id = 999999 WHERE id = 9001"))
                .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("거래별 약속 종류 중복 및 알 수 없는 종류 거부")
    void slotKindConstraintsAreEnforced() {
        createSlot();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO appointment_slots (rental_id, kind, created_at, updated_at)
                VALUES (9001, 'PICKUP', NOW(6), NOW(6))
                """)).isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(SQLException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE appointment_slots SET kind = 'UNKNOWN' WHERE id = 9001"))
                .isInstanceOf(DataAccessException.class).hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("대기 요청은 같은 회원만 중복이며 점유 기간에는 포함하지 않음")
    void pendingOverlapOnlyBlocksSameBorrower() {
        var start = java.time.LocalDate.of(2026, 10, 11);
        var end = java.time.LocalDate.of(2026, 10, 12);
        assertThat(rentals.hasPendingRequest(9001L, 9002L, start, end)).isTrue();
        assertThat(rentals.hasPendingRequest(9001L, 9003L, start, end)).isFalse();
        assertThat(rentals.hasOccupiedDates(9001L, java.util.List.of(
                RentalStatus.APPROVED, RentalStatus.ACTIVE, RentalStatus.RETURNED), start, end)).isFalse();
    }

    @Test
    @DisplayName("반납 완료도 종료일을 포함해 점유하고 다음 날부터 겹치지 않음")
    void returnedRentalRetainsInclusiveOccupancy() {
        jdbc.update("UPDATE rentals SET status = 'RETURNED' WHERE id = 9001");
        var statuses = java.util.List.of(RentalStatus.APPROVED, RentalStatus.ACTIVE, RentalStatus.RETURNED);
        var last = java.time.LocalDate.of(2026, 10, 11);
        assertThat(rentals.hasOccupiedDates(9001L, statuses, last, last.plusDays(1))).isTrue();
        assertThat(rentals.hasOccupiedDates(9001L, statuses, last.plusDays(1), last.plusDays(2))).isFalse();
    }

    @Test
    @DisplayName("기한 도달 시 ACTIVE만 연체로 판정")
    void overdueRequiresActiveAndDueTime() {
        Instant due = Instant.parse("2026-10-11T15:00:00Z");
        jdbc.update("UPDATE rentals SET status = 'ACTIVE' WHERE id = 9001");
        assertThat(rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(9001L, RentalStatus.ACTIVE,
                due.minusSeconds(1))).isFalse();
        assertThat(rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(9001L, RentalStatus.ACTIVE, due)).isTrue();
        jdbc.update("UPDATE rentals SET status = 'RETURNED' WHERE id = 9001");
        assertThat(rentals.existsByItemIdAndStatusAndDueAtLessThanEqual(9001L, RentalStatus.ACTIVE, due)).isFalse();
    }

    private void createSlot() {
        jdbc.update("""
                INSERT INTO appointment_slots (id, rental_id, kind, created_at, updated_at)
                VALUES (9001, 9001, 'PICKUP', NOW(6), NOW(6))
                """);
    }
}
