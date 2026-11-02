package com.billim.domain.rental.service;

import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.notification.service.OutboxEventPublisher;
import com.billim.domain.rental.dto.AppointmentSlotResponse;
import com.billim.domain.rental.dto.AppointmentVersionRequest;
import com.billim.domain.rental.dto.ProposeAppointmentRequest;
import com.billim.domain.rental.entity.AppointmentKind;
import com.billim.global.config.JpaAuditingConfig;
import com.billim.global.json.JsonCodec;
import com.billim.global.security.LoginMember;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestReporter;
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
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true"})
@Import({AppointmentCommandQueryDbTest.Config.class, JpaAuditingConfig.class, AppointmentService.class,
        AppointmentCommandService.class, RentalRequestEffects.class, OutboxEventPublisher.class, JsonCodec.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppointmentCommandQueryDbTest {
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
    @Autowired AppointmentCommandService commands;
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
        jdbc.update("INSERT INTO appointment_slots (rental_id,kind,created_at,updated_at) VALUES (9800,'PICKUP',NOW(6),NOW(6)),(9800,'RETURN',NOW(6),NOW(6))");
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
        jdbc.update("DELETE FROM members WHERE id IN (9800,9801)");
    }

    @ParameterizedTest
    @CsvSource({"PICKUP,false", "PICKUP,true", "RETURN,false", "RETURN,true"})
    @DisplayName("전달·반납 변경 응답은 다른 슬롯 상태와 무관하게 전체 조회와 일치")
    void commandsMatchReadResponse(AppointmentKind kind, boolean populatedOther, TestReporter reporter) {
        if (populatedOther) {
            AppointmentKind other = kind == AppointmentKind.PICKUP ? AppointmentKind.RETURN : AppointmentKind.PICKUP;
            var proposed = propose(other, 0, "9801");
            commands.accept(Long.valueOf(proposed.pendingProposal().id()), new AppointmentVersionRequest(1L), key(), OWNER);
            propose(other, 2, "9801");
        }
        var untouched = queries.getAppointments(9800L, BORROWER).items().stream()
                .filter(slot -> slot.kind() != kind).findFirst().orElseThrow();
        String proposalKey = key();
        var request = request(kind, 0, "9800");
        var proposed = measured("propose", kind, populatedOther, reporter,
                () -> commands.propose(9800L, kind, request, proposalKey, BORROWER));
        assertResponse(proposed, BORROWER, 1);
        assertThat(proposed.allowedActions()).containsExactly("PROPOSE", "WITHDRAW");
        var accepted = measured("accept", kind, populatedOther, reporter,
                () -> commands.accept(Long.valueOf(proposed.pendingProposal().id()), new AppointmentVersionRequest(1L), key(), OWNER));
        assertResponse(accepted, OWNER, 2);
        assertThat(accepted.pendingProposal()).isNull();
        assertThat(accepted.confirmedProposal().id()).isEqualTo(proposed.pendingProposal().id());
        var changed = measured("change-proposal", kind, populatedOther, reporter, () -> propose(kind, 2, "9800"));
        assertResponse(changed, BORROWER, 3);
        assertThat(changed.confirmedProposal()).isEqualTo(accepted.confirmedProposal());
        var withdrawn = measured("withdraw", kind, populatedOther, reporter,
                () -> commands.withdraw(Long.valueOf(changed.pendingProposal().id()), new AppointmentVersionRequest(3L), key(), BORROWER));
        assertResponse(withdrawn, BORROWER, 4);
        assertThat(withdrawn.pendingProposal()).isNull();
        assertThat(withdrawn.confirmedProposal()).isEqualTo(accepted.confirmedProposal());
        var replacement = measured("replacement-proposal", kind, populatedOther, reporter, () -> propose(kind, 4, "9800"));
        assertResponse(replacement, BORROWER, 5);
        var replaced = measured("replace-confirmed", kind, populatedOther, reporter,
                () -> commands.accept(Long.valueOf(replacement.pendingProposal().id()), new AppointmentVersionRequest(5L), key(), OWNER));
        assertResponse(replaced, OWNER, 6);
        assertThat(replaced.confirmedProposal().id()).isEqualTo(replacement.pendingProposal().id());
        assertThat(jdbc.queryForObject("SELECT status FROM appointment_proposals WHERE id=?", String.class,
                accepted.confirmedProposal().id())).isEqualTo("SUPERSEDED");
        assertThat(queries.getAppointments(9800L, BORROWER).items()).contains(untouched);
        assertThat(commands.propose(9800L, kind, request, proposalKey, BORROWER))
                .usingRecursiveComparison().withComparatorForType(java.math.BigDecimal::compareTo, java.math.BigDecimal.class)
                .isEqualTo(proposed);
        int expectedEffects = populatedOther ? 9 : 6;
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(expectedEffects);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_records", Integer.class)).isEqualTo(expectedEffects);
    }

    private AppointmentSlotResponse measured(String action, AppointmentKind kind, boolean populatedOther,
                                              TestReporter reporter, Supplier<AppointmentSlotResponse> command) {
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var response = command.get();
        long statements = stats.getPrepareStatementCount();
        reporter.publishEntry(Map.of("action", action, "kind", kind.name(), "populatedOther", Boolean.toString(populatedOther),
                "sql", Long.toString(statements)));
        long limit = switch (action) {
            case "propose" -> 16;
            case "replace-confirmed" -> 20;
            default -> 18;
        };
        assertThat(statements).as("%s/%s, 다른 슬롯 제안=%s", kind, action, populatedOther).isLessThanOrEqualTo(limit);
        return response;
    }

    private void assertResponse(AppointmentSlotResponse response, LoginMember member, long version) {
        assertThat(response.version()).isEqualTo(version);
        assertThat(queries.getAppointments(9800L, member).items()).contains(response);
    }

    private AppointmentSlotResponse propose(AppointmentKind kind, long version, String place) {
        return commands.propose(9800L, kind, request(kind, version, place), key(), BORROWER);
    }

    private ProposeAppointmentRequest request(AppointmentKind kind, long version, String place) {
        return new ProposeAppointmentRequest(version,
                NOW.plusSeconds((kind == AppointmentKind.RETURN ? 86400 : 0) + 3600 + version * 60), place);
    }

    private String key() { return UUID.randomUUID().toString(); }
}
