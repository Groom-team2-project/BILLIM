package com.billim.domain.rental.service;

import com.billim.domain.community.service.CommunityMembershipService;
import com.billim.domain.community.service.CurrentCommunityStore;
import com.billim.domain.safety.service.BlockService;
import com.billim.domain.safety.service.SanctionService;
import com.billim.domain.safety.service.MemberRowLock;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.rental.dto.*;
import com.billim.domain.rental.entity.RentalStatus;
import com.billim.global.security.LoginMember;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;
import javax.sql.DataSource;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.generate_statistics=true", "logging.level.org.hibernate.stat=OFF"})
@Import({RentalApprovalListDbTest.Config.class, RentalQueryService.class, AppointmentService.class,
        RentalRequestPolicy.class, CommunityMembershipService.class, CurrentCommunityStore.class,
        BlockService.class, SanctionService.class, MemberRowLock.class})
class RentalApprovalListDbTest {
    private static final LoginMember OWNER = new LoginMember(9600L, MemberRole.USER);
    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean @ServiceConnection MySQLContainer mysql() { return new MySQLContainer("mysql:8.4"); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-10-09T15:00:00Z"), ZoneOffset.UTC); }
    }
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired RentalQueryService queries;
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO communities(id,name,display_address,center_latitude,center_longitude,verification_radius_m,created_at,updated_at) VALUES(9600,'동네','공용 주소',37,127,500,NOW(6),NOW(6)),(9601,'다른 동네','공용 주소',37,127,500,NOW(6),NOW(6))");
        jdbc.update("INSERT INTO community_places(id,community_id,name,latitude,longitude,active,created_at,updated_at) VALUES(9600,9600,'정문',37,127,TRUE,NOW(6),NOW(6))");
        for (long id=9600;id<=9620;id++) {
            // active_community_id는 현재 동네다. 저장 동네가 둘까지 되면서 소속 판정 창구가
            // 이 값을 기준으로 바뀌었다 — 비워 두면 유효 소속이 없는 회원이 된다 (V23)
            jdbc.update("INSERT INTO members(id,display_name,active_community_id,created_at,updated_at) VALUES(?,'합성 회원',9600,NOW(6),NOW(6))",id);
            jdbc.update("INSERT INTO community_memberships(member_id,community_id,status,active_slot,verified_at,verification_expires_at,created_at,updated_at) VALUES(?,9600,'ACTIVE',1,'2026-10-01','2026-11-01',NOW(6),NOW(6))",id);
        }
        for (long id=9601;id<=9620;id++) {
            jdbc.update("INSERT INTO items(id,owner_id,community_id,category_id,place_id,title,available_start_date,available_end_date,visibility,created_at,updated_at) SELECT ?,9600,9600,id,9600,'물건','2026-10-01','2026-11-01','PUBLIC',NOW(6),NOW(6) FROM categories ORDER BY id LIMIT 1",id);
            jdbc.update("INSERT INTO rentals(id,item_id,owner_id,borrower_id,community_id,start_date,end_date,status,request_expires_at,due_at,item_title_snapshot,place_name_snapshot,place_id,created_at,updated_at) VALUES(?,?,9600,?,9600,'2026-10-10','2026-10-11','REQUESTED','2026-10-10 15:00:00','2026-10-11 15:00:00','제목','정문',9600,'2026-10-09',NOW(6))",id,id,id);
        }
    }

    @Test @DisplayName("실제 정책을 적용한 요청 20건은 고정 SQL 상한과 단건 응답 유지")
    void boundsQueriesWithRealPolicies() {
        em.clear();var stats=emf.unwrap(SessionFactory.class).getStatistics();stats.clear();
        var response=list();
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(13);
        assertThat(response.items()).hasSize(20).allSatisfy(r -> assertThat(r.allowedActions()).containsExactly("APPROVE","REJECT"));
        sameAsDetails(response);
    }

    @ParameterizedTest
    @ValueSource(strings={"membership-expired","membership-left","membership-other","owner-expired","blocked-forward","blocked-reverse","withdrawn","owner-withdrawn","place-inactive","place-other","place-missing","hidden","deleted","sanction-start","sanction-end","sanction-future","sanction-revoked","owner-sanction"})
    @DisplayName("목록과 단건은 소속·양방향 차단·활성 회원·장소·제재 경계에서 같은 승인 판정")
    void preservesPolicyDecisions(String scenario) {
        switch(scenario) {
            case "membership-expired" -> jdbc.update("UPDATE community_memberships SET verification_expires_at='2026-10-09 15:00:00' WHERE member_id=9601");
            case "membership-left" -> jdbc.update("UPDATE community_memberships SET status='LEFT',active_slot=NULL,left_at=NOW(6) WHERE member_id=9601");
            case "membership-other" -> jdbc.update("UPDATE community_memberships SET community_id=9601 WHERE member_id=9601");
            case "owner-expired" -> jdbc.update("UPDATE community_memberships SET verification_expires_at='2026-10-09 15:00:00' WHERE member_id=9600");
            case "blocked-forward" -> jdbc.update("INSERT INTO blocks(blocker_id,blocked_id,created_at,updated_at) VALUES(9600,9601,NOW(6),NOW(6))");
            case "blocked-reverse" -> jdbc.update("INSERT INTO blocks(blocker_id,blocked_id,created_at,updated_at) VALUES(9601,9600,NOW(6),NOW(6))");
            case "withdrawn" -> jdbc.update("UPDATE members SET status='WITHDRAWN' WHERE id=9601");
            case "owner-withdrawn" -> jdbc.update("UPDATE members SET status='WITHDRAWN' WHERE id=9600");
            case "place-inactive" -> jdbc.update("UPDATE community_places SET active=FALSE WHERE id=9600");
            case "place-other" -> jdbc.update("UPDATE community_places SET community_id=9601 WHERE id=9600");
            case "place-missing" -> jdbc.update("UPDATE rentals SET place_id=9699 WHERE id=9601");
            case "hidden" -> jdbc.update("UPDATE items SET visibility='HIDDEN' WHERE id=9601");
            case "deleted" -> jdbc.update("UPDATE items SET visibility='DELETED',deleted_at=NOW(6) WHERE id=9601");
            default -> sanction(scenario);
        }
        em.clear();
        if(scenario.equals("owner-withdrawn")) {
            assertThatThrownBy(this::list).isInstanceOf(com.billim.global.exception.BusinessException.class);
            return;
        }
        var response=list();
        var first=response.items().stream().filter(r -> r.id().equals("9601")).findFirst().orElseThrow();
        boolean allowed=List.of("sanction-end","sanction-future","sanction-revoked").contains(scenario);
        assertThat(first.allowedActions()).isEqualTo(allowed?List.of("APPROVE","REJECT"):List.of("REJECT"));
        sameAsDetails(response);
    }

    private void sanction(String scenario) {
        long member=scenario.equals("owner-sanction")?9600:9601;
        long reporter=member==9600?9602:9600;
        jdbc.update("INSERT INTO reports(id,reporter_id,target_type,target_member_id,community_id,reason_code,target_snapshot,created_at,updated_at) VALUES(9600,?,'MEMBER',?,9600,'ABUSE','{}',NOW(6),NOW(6))",reporter,member);
        jdbc.update("INSERT INTO report_actions(id,report_id,admin_id,action,public_reason,created_at,updated_at) VALUES(9600,9600,9600,'SUSPEND_7D','사유',NOW(6),NOW(6))");
        String start=scenario.equals("sanction-future")?"2026-10-09 15:00:00.000001":"2026-10-09 15:00:00";
        String end=scenario.equals("sanction-end")?"2026-10-09 15:00:00":"2026-10-10 15:00:00";
        if(scenario.equals("sanction-end"))start="2026-10-08 15:00:00";
        jdbc.update("INSERT INTO sanctions(member_id,report_action_id,kind,starts_at,ends_at,created_at,updated_at) VALUES(?,9600,'SUSPEND',?,?,NOW(6),NOW(6))",member,start,end);
        if(scenario.equals("sanction-revoked"))jdbc.update("UPDATE sanctions SET revoked_at=NOW(6),revoked_by=9600,revoke_reason='해제' WHERE report_action_id=9600");
    }
    private RentalListResponse list() {return queries.getRentalList(RentalRole.OWNER,RentalStatus.REQUESTED,false,null,0,20,OWNER);}
    private void sameAsDetails(RentalListResponse response) {
        em.clear();response.items().forEach(r -> assertThat(r).isEqualTo(queries.getRentalDetailStatus(Long.valueOf(r.id()),OWNER).rental()));
    }
}
