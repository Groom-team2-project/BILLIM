package com.billim.domain.rental.service;

import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.rental.dto.AppointmentSlotsResponse;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.json.JsonCodec;
import com.billim.global.security.LoginMember;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true", "logging.level.org.hibernate.stat=OFF"})
@Import({AppointmentReadQueryDbTest.Config.class, JpaAuditingConfig.class, AppointmentService.class,
        JsonCodec.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppointmentReadQueryDbTest {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final LoginMember OWNER = new LoginMember(9800L, MemberRole.USER);
    private static final LoginMember BORROWER = new LoginMember(9801L, MemberRole.USER);

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean @ServiceConnection MySQLContainer mysql() { return new MySQLContainer("mysql:8.4"); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory emf;
    @Autowired JsonCodec json;
    @Autowired AppointmentService queries;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO members (id,display_name,created_at,updated_at) VALUES (9800,'소유자',NOW(6),NOW(6)),(9801,'요청자',NOW(6),NOW(6))");
        jdbc.update("INSERT INTO communities (id,name,display_address,center_latitude,center_longitude,verification_radius_m,created_at,updated_at) VALUES (9800,'측정 동네','공용 주소',37,127,500,NOW(6),NOW(6))");
        jdbc.update("INSERT INTO community_places (id,community_id,name,latitude,longitude,active,created_at,updated_at) VALUES (9800,9800,'정문',37,127,true,NOW(6),NOW(6)),(9801,9800,'후문',37.1,127.1,true,NOW(6),NOW(6))");
        jdbc.update("""
                INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,
                  available_start_date,available_end_date,visibility,created_at,updated_at)
                SELECT 9800,9800,9800,id,9800,'드릴','2026-10-01','2026-11-30','PUBLIC',NOW(6),NOW(6)
                FROM categories ORDER BY id LIMIT 1
                """);
        jdbc.update("""
                INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,
                  request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,created_at,updated_at)
                VALUES (9800,9800,9800,9801,9800,'2026-10-10','2026-10-11','APPROVED',
                  '2026-10-10 15:00:00','2026-10-11 15:00:00','드릴','정문',9800,NOW(6),NOW(6))
                """);
        jdbc.update("INSERT INTO appointment_slots (id,rental_id,kind,created_at,updated_at) VALUES (9800,9800,'PICKUP',NOW(6),NOW(6)),(9801,9800,'RETURN',NOW(6),NOW(6))");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM idempotency_records WHERE member_id IN (9800,9801)");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("UPDATE appointment_slots SET confirmed_proposal_id=NULL WHERE rental_id=9800");
        jdbc.update("DELETE FROM appointment_proposals WHERE slot_id IN (SELECT id FROM appointment_slots WHERE rental_id=9800)");
        jdbc.update("DELETE FROM appointment_slots WHERE rental_id=9800");
        jdbc.update("DELETE FROM rentals WHERE id=9800");
        jdbc.update("DELETE FROM items WHERE id=9800");
        jdbc.update("DELETE FROM community_places WHERE community_id=9800");
        jdbc.update("DELETE FROM communities WHERE id=9800");
        jdbc.update("DELETE FROM community_places WHERE community_id=9900");
        jdbc.update("DELETE FROM communities WHERE id=9900");
        jdbc.update("DELETE FROM members WHERE id IN (9800,9801)");
    }

    @ParameterizedTest
    @CsvSource({"NO_SLOTS", "EMPTY", "PENDING", "CONFIRMED", "BOTH_SHARED", "BOTH_DISTINCT"})
    @DisplayName("약속 전체 조회는 단일 슬롯 응답과 일치하며 상태별 조회 비용 계측")
    void readsMatchSingleSlots(String scenario, TestReporter reporter) {
        seedProposals(scenario);
        var expected = scenario.equals("NO_SLOTS") ? new AppointmentSlotsResponse(List.of())
                : new AppointmentSlotsResponse(List.of(queries.getAppointment(9800L, 9800L, BORROWER),
                    queries.getAppointment(9800L, 9801L, BORROWER)));
        for (int i = 0; i < 20; i++) assertThat(queries.getAppointments(9800L, BORROWER)).isEqualTo(expected);
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        for (int run = 1; run <= 3; run++) {
            List<Long> sql = new ArrayList<>();
            List<Long> nanos = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                stats.clear();
                long started = System.nanoTime();
                var response = queries.getAppointments(9800L, BORROWER);
                nanos.add(System.nanoTime() - started);
                sql.add(stats.getPrepareStatementCount());
                assertThat(response).isEqualTo(expected);
                long limit = switch (scenario) {
                    case "NO_SLOTS" -> 3;
                    case "EMPTY" -> 4;
                    case "PENDING" -> 5;
                    default -> 6;
                };
                assertThat(sql.getLast()).isLessThanOrEqualTo(limit);
            }
            reporter.publishEntry("appointmentRead", json.write(Map.of("scenario", scenario, "run", run,
                    "sql", sql, "nanos", nanos, "response", expected)));
        }
    }

    @Test
    @DisplayName("과거 제안 이력과 참조 없는 확정 제안은 응답에서 제외하고 빈 슬롯 유지")
    void ignoresHistoryAndUnreferencedConfirmedProposals() {
        seedProposals("BOTH_DISTINCT");
        for (int i = 0; i < 100; i++) {
            insertProposal(10000 + i, 9800, 9800, NOW.plusSeconds(3600), "SUPERSEDED");
        }
        jdbc.update("UPDATE appointment_slots SET confirmed_proposal_id=NULL WHERE id=9801");
        jdbc.update("DELETE FROM appointment_proposals WHERE slot_id=9801 AND status='PROPOSED'");
        assertWholeMatchesSingles(BORROWER);
        var response = queries.getAppointments(9800L, BORROWER);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().getLast().confirmedProposal()).isNull();
        assertThat(response.items().getLast().pendingProposal()).isNull();
    }

    @Test
    @DisplayName("비활성 장소 정보와 제안 당시 이름 유지, 다른 동네 장소의 좌표·안내 제외")
    void preservesPlaceSnapshotAndCommunityBoundary() {
        seedProposals("BOTH_DISTINCT");
        jdbc.update("UPDATE community_places SET active=false,guide='비활성 장소 안내' WHERE id=9801");
        jdbc.update("INSERT INTO communities (id,name,display_address,center_latitude,center_longitude,verification_radius_m,created_at,updated_at) VALUES (9900,'다른 동네','공용 주소',38,128,500,NOW(6),NOW(6))");
        jdbc.update("INSERT INTO community_places (id,community_id,name,latitude,longitude,guide,active,created_at,updated_at) VALUES (9900,9900,'다른 장소',38,128,'다른 동네 안내',true,NOW(6),NOW(6))");
        jdbc.update("UPDATE appointment_proposals SET place_id=9900 WHERE id=9801");
        assertWholeMatchesSingles(BORROWER);
        var response = queries.getAppointments(9800L, BORROWER);
        var foreign = response.items().getFirst().pendingProposal().place();
        assertThat(foreign.name()).isEqualTo("제안 당시 장소");
        assertThat(foreign.latitude()).isNull();
        assertThat(foreign.longitude()).isNull();
        assertThat(foreign.guide()).isNull();
        var inactive = response.items().getLast().pendingProposal().place();
        assertThat(inactive.name()).isEqualTo("제안 당시 장소");
        assertThat(inactive.latitude()).isNotNull();
        assertThat(inactive.guide()).isEqualTo("비활성 장소 안내");
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "ACTIVE", "RETURNED"})
    @DisplayName("양 당사자의 허용 행동은 거래 상태별 단일 슬롯 조회와 동일")
    void preservesActionsForBothParticipants(String status) {
        seedProposals("BOTH_DISTINCT");
        jdbc.update("UPDATE rentals SET status=? WHERE id=9800", status);
        assertWholeMatchesSingles(OWNER);
        assertWholeMatchesSingles(BORROWER);
    }

    private void assertWholeMatchesSingles(LoginMember member) {
        assertThat(queries.getAppointments(9800L, member)).isEqualTo(new AppointmentSlotsResponse(List.of(
                queries.getAppointment(9800L, 9800L, member), queries.getAppointment(9800L, 9801L, member))));
    }

    private void seedProposals(String scenario) {
        if (scenario.equals("NO_SLOTS")) {
            jdbc.update("DELETE FROM appointment_slots WHERE rental_id=9800");
            return;
        }
        for (int i = 0; i < 2; i++) {
            long slot = 9800 + i;
            long confirmedId = 9800 + i * 10;
            long pendingId = confirmedId + 1;
            Instant at = NOW.plusSeconds(i * 86400L + 3600);
            if (scenario.equals("CONFIRMED") || scenario.startsWith("BOTH")) {
                insertProposal(confirmedId, slot, 9800, at, "CONFIRMED");
                jdbc.update("UPDATE appointment_slots SET confirmed_proposal_id=? WHERE id=?", confirmedId, slot);
            }
            if (scenario.equals("PENDING") || scenario.startsWith("BOTH")) {
                insertProposal(pendingId, slot, scenario.equals("BOTH_DISTINCT") ? 9801 : 9800,
                        at.plusSeconds(60), "PROPOSED");
            }
        }
    }

    private void insertProposal(long id, long slot, long place, Instant at, String status) {
        jdbc.update("""
                INSERT INTO appointment_proposals (id,slot_id,proposer_id,place_id,scheduled_at,
                  place_name_snapshot,status,accepted_by,accepted_at,created_at,updated_at)
                VALUES (?,?,9800,?,?,'제안 당시 장소',?,?,?,NOW(6),NOW(6))
                """, id, slot, place, LocalDateTime.ofInstant(at, ZoneOffset.UTC), status,
                status.equals("CONFIRMED") ? 9801L : null,
                status.equals("CONFIRMED") ? LocalDateTime.ofInstant(NOW, ZoneOffset.UTC) : null);
    }
}
