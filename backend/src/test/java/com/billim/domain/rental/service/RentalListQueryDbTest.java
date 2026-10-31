package com.billim.domain.rental.service;

import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.rental.dto.RentalRole;
import com.billim.domain.rental.dto.RentalSummaryResponse;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.global.security.LoginMember;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true"})
@Import({RentalListQueryDbTest.Config.class, RentalQueryService.class, AppointmentService.class})
class RentalListQueryDbTest {
    private static final Instant NOW = Instant.parse("2026-10-09T15:00:00Z");
    private static final LoginMember OWNER = new LoginMember(9600L, MemberRole.USER);
    private static final LoginMember BORROWER = new LoginMember(9601L, MemberRole.USER);

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean @ServiceConnection MySQLContainer mysql() { return new MySQLContainer("mysql:8.4"); }
        @Bean Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired RentalQueryService queries;
    @MockitoBean RentalRequestPolicy policy;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        for (long id : new long[]{9600, 9601}) member(id);
        for (long id = 9620; id < 9640; id++) member(id);
        when(policy.canApprove(any(), any())).thenReturn(true);
    }

    private void member(long id) {
        jdbc.update("INSERT INTO members (id,display_name,created_at,updated_at) VALUES (?, '합성 회원', NOW(6), NOW(6))", id);
    }

    private void seed(boolean owner) {
        for (int i = 0; i < 20; i++) {
            long item = 9600 + i;
            long ownerId = owner ? 9600 : 9620 + i;
            long borrowerId = owner ? 9620 + i : 9601;
            jdbc.update("""
                    INSERT INTO items (id,owner_id,community_id,category_id,place_id,title,
                      available_start_date,available_end_date,visibility,created_at,updated_at)
                    SELECT ?,?,1,id,1,'물건','2026-10-01','2026-10-31','PUBLIC',NOW(6),NOW(6)
                    FROM categories ORDER BY id LIMIT 1
                    """, item, ownerId);
            jdbc.update("""
                    INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,
                      status,request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,created_at,updated_at)
                    VALUES (?,?,?,?,1,'2026-10-10','2026-10-12','REQUESTED','2026-10-10 15:00:00',
                      '2026-10-12 15:00:00','거래 당시 제목','정문',1,'2026-10-09 00:00:00',NOW(6))
                    """, item, item, ownerId, borrowerId);
        }
    }

    @Test
    @DisplayName("서로 다른 소유자 20명의 빌린 목록은 고정 조회 수와 단건 응답 유지")
    void borrowerListHasBoundedQueriesAndSameResponse() {
        seed(false);
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var response = queries.getRentalList(RentalRole.BORROWER, null, false, null, 0, 20, BORROWER);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
        assertThat(response.items()).hasSize(20);
        assertThat(response.totalElements()).isEqualTo(20);
        assertThat(response.items()).extracting(RentalSummaryResponse::id).startsWith("9619", "9618");
        assertThat(response.items()).allSatisfy(r -> assertThat(r.allowedActions()).containsExactly("CANCEL"));
        em.clear();
        response.items().forEach(r -> assertThat(r).isEqualTo(queries.getRentalDetailStatus(Long.valueOf(r.id()), BORROWER).rental()));
    }

    @Test
    @DisplayName("소유자의 대기 요청 20건은 일괄 조회 후 단건 조회와 같은 승인 버튼 유지")
    void ownerRequestsHaveBoundedQueriesAndSameResponse() {
        seed(true);
        em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var response = queries.getRentalList(RentalRole.OWNER, RentalStatus.REQUESTED, false, null, 0, 20, OWNER);
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(7);
        assertThat(response.items()).hasSize(20);
        assertThat(response.items()).allSatisfy(r -> assertThat(r.allowedActions()).containsExactly("APPROVE", "REJECT"));
        em.clear();
        response.items().forEach(r -> assertThat(r).isEqualTo(queries.getRentalDetailStatus(Long.valueOf(r.id()), OWNER).rental()));
    }

    @Test
    @DisplayName("일괄 조회도 반납 완료 경계 점유·연체 시각·전달 제한·만료·정책 거부 보존")
    void listPreservesOccupancyAndTimeBoundaries() {
        seed(true);
        occupied(9700, 9600, "RETURNED", "2026-10-08", "2026-10-10", "2026-10-09 15:00:00");
        occupied(9701, 9601, "ACTIVE", "2026-10-07", "2026-10-08", "2026-10-09 15:00:00");
        occupied(9702, 9602, "ACTIVE", "2026-10-07", "2026-10-08", "2026-10-09 15:00:00.000001");
        occupied(9703, 9603, "ACTIVE", "2026-10-07", "2026-10-08", "2026-10-11 15:00:00");
        jdbc.update("UPDATE rentals SET status='APPROVED' WHERE id IN (9603,9604)");
        jdbc.update("UPDATE rentals SET request_expires_at='2026-10-09 15:00:00' WHERE id=9605");
        jdbc.update("UPDATE rentals SET start_date='2026-10-09' WHERE id=9606");
        when(policy.canApprove(any(), org.mockito.ArgumentMatchers.argThat(r -> r.getId().equals(9607L)))).thenReturn(false);
        em.clear();
        Map<String, RentalSummaryResponse> response = queries.getRentalList(RentalRole.OWNER, null, false, null, 0, 50, OWNER)
                .items().stream().collect(Collectors.toMap(RentalSummaryResponse::id, Function.identity()));
        for (String id : new String[]{"9600", "9601", "9605", "9606", "9607"}) {
            assertThat(response.get(id).allowedActions()).containsExactly("REJECT");
        }
        assertThat(response.get("9602").allowedActions()).containsExactly("APPROVE", "REJECT");
        assertThat(response.get("9603").allowedActions()).containsExactly("CANCEL");
        assertThat(response.get("9604").allowedActions()).containsExactly("CANCEL", "HANDOVER");
        em.clear();
        response.values().forEach(r -> assertThat(r).isEqualTo(queries.getRentalDetailStatus(Long.valueOf(r.id()), OWNER).rental()));
    }

    private void occupied(long id, long item, String status, String start, String end, String due) {
        jdbc.update("""
                INSERT INTO rentals (id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,
                  request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,created_at,updated_at)
                VALUES (?,?,9600,9601,1,?,?,?,'2026-10-10 15:00:00',?,'이전 거래','정문',1,NOW(6),NOW(6))
                """, id, item, start, end, status, due);
    }
}
