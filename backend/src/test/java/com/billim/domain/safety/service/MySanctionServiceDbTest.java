package com.billim.domain.safety.service;

import com.billim.MockChatRedisListener;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.safety.dto.MySanctionPageResponse;
import com.billim.domain.safety.dto.MySanctionResponse;
import com.billim.domain.safety.dto.CreateReportRequest;
import com.billim.domain.safety.dto.ResolveReportRequest;
import com.billim.domain.safety.dto.ReportResponse;
import com.billim.domain.safety.entity.ReportActionType;
import com.billim.domain.safety.entity.ReportReasonCode;
import com.billim.domain.safety.entity.ReportTargetType;
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
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내가 받은 운영 조치 내역 (API 명세 12.7 E_072).
 *
 * <p>알림이 "자세한 내용은 내 프로필에서 확인해 주세요"로 안내하는데 확인 경로가 없었다.
 * 이 조회가 그 경로다. 무엇을 담고 무엇을 담지 않는지가 이 테스트의 핵심이다.
 */
@Import(MySanctionServiceDbTest.MySql.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.notification.outbox.enabled=false"
})
@MockChatRedisListener
class MySanctionServiceDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    private static final long COMMUNITY = 100L;

    @Autowired MySanctionService mySanctionService;
    @Autowired ReportService reportService;
    @Autowired AdminReportService adminReportService;
    @Autowired SanctionService sanctionService;
    @Autowired MemberRepository memberRepository;
    @Autowired JdbcTemplate jdbc;

    private long reporter;
    private long offender;
    private long admin;

    @BeforeEach
    void setUp() {
        clean();
        reporter = saveMember("소빈", MemberRole.USER);
        offender = saveMember("정우", MemberRole.USER);
        admin = saveMember("운영자", MemberRole.ADMIN);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ---------- 담는 것 ----------

    @Test
    @DisplayName("경고를 받으면 조치 종류와 최소 사유가 내려온다. 이게 없어서 대상이 이유를 몰랐다")
    void warnDeliversActionAndPublicReason() {
        resolve(report(), ReportActionType.WARN, "반납 약속을 두 번 어겼습니다. 다음부터는 미리 알려 주세요.");

        MySanctionResponse row = mine().items().getFirst();

        assertThat(row.action()).isEqualTo("WARN");
        assertThat(row.publicReason())
                .isEqualTo("반납 약속을 두 번 어겼습니다. 다음부터는 미리 알려 주세요.");
        assertThat(row.decidedAt()).isNotNull();
    }

    @Test
    @DisplayName("경고는 제재 행이 없어 기간이 비고 유효하지 않다. sanctions만 보면 아예 안 보인다")
    void warnHasNoSanctionPeriod() {
        resolve(report(), ReportActionType.WARN, "가벼운 1회 위반입니다.");

        MySanctionResponse row = mine().items().getFirst();

        assertThat(row.kind()).isNull();
        assertThat(row.startsAt()).isNull();
        assertThat(row.endsAt()).isNull();
        assertThat(row.isEffective()).isFalse();
        // 제재 테이블에는 행이 없다 — 이 조회가 report_actions를 읽는 이유다
        assertThat(count("sanctions")).isZero();
    }

    @Test
    @DisplayName("일시 정지는 종류와 기간이 함께 내려온다")
    void suspensionDeliversKindAndPeriod() {
        resolve(report(), ReportActionType.SUSPEND_7D, "금전을 요구했습니다.");

        MySanctionResponse row = mine().items().getFirst();

        assertThat(row.action()).isEqualTo("SUSPEND_7D");
        assertThat(row.kind()).isEqualTo("SUSPEND");
        assertThat(row.startsAt()).isNotNull();
        assertThat(row.endsAt()).isNotNull();
        assertThat(row.isEffective()).isTrue();
    }

    @Test
    @DisplayName("영구 정지는 끝이 없다")
    void banHasNoEnd() {
        resolve(report(), ReportActionType.BAN, "사기 행위가 확인됐습니다.");

        MySanctionResponse row = mine().items().getFirst();

        assertThat(row.kind()).isEqualTo("BAN");
        assertThat(row.endsAt()).isNull();
        assertThat(row.isEffective()).isTrue();
    }

    @Test
    @DisplayName("해제된 제재도 내역에 남고 유효하지 않다고 표시된다")
    void revokedSanctionStaysInHistory() {
        resolve(report(), ReportActionType.SUSPEND_7D, "확인 결과 조치했습니다.");
        String sanctionId = jdbc.queryForObject("SELECT id FROM sanctions", String.class);
        sanctionService.revoke(admin, Long.parseLong(sanctionId), "이의 제기가 인정됐습니다.");

        MySanctionResponse row = mine().items().getFirst();

        assertThat(row.revokedAt()).isNotNull();
        assertThat(row.isEffective()).isFalse();
    }

    // ---------- 담지 않는 것 ----------

    @Test
    @DisplayName("반려는 내역에 넣지 않는다. 넣으면 신고당한 사실만 알려 주게 된다")
    void dismissIsNotListed() {
        resolve(report(), ReportActionType.DISMISS, "문제없음으로 반려합니다.");

        assertThat(mine().items()).isEmpty();
        assertThat(mine().totalElements()).isZero();
    }

    @Test
    @DisplayName("응답에 신고자·운영 메모·신고 사유 코드·대상 거래가 없다")
    void hidesReporterAndInternalNote() {
        long reportId = report();
        adminReportService.resolve(admin, reportId, new ResolveReportRequest(
                0L, ReportActionType.WARN, "공개 사유입니다.", "내부 메모 — 노출 금지"));

        MySanctionResponse row = mine().items().getFirst();

        // record의 필드가 이것뿐이다. 늘어나면 이 단정이 깨져 검토하게 된다
        assertThat(MySanctionResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "action", "publicReason", "decidedAt",
                        "kind", "startsAt", "endsAt", "revokedAt", "isEffective");
        assertThat(row.publicReason()).doesNotContain("내부 메모");
    }

    // ---------- 범위와 순서 ----------

    @Test
    @DisplayName("내가 받은 조치만 본다. 내가 신고해서 다른 사람이 받은 조치는 안 보인다")
    void listsOnlyActionsReceivedByMe() {
        // 소빈이 정우를 신고해 정우가 경고를 받는다
        resolve(report(), ReportActionType.WARN, "정우에게 내린 조치입니다.");

        // 신고자(소빈)의 내역은 비어 있다
        assertThat(mySanctionService.findMySanctions(reporter, 0, 20).items()).isEmpty();
        // 대상(정우)의 내역에만 있다
        assertThat(mine().items()).hasSize(1);
    }

    @Test
    @DisplayName("최근 조치가 먼저 온다")
    void newestFirst() {
        resolve(report(), ReportActionType.WARN, "첫 번째 조치");
        resolve(report(), ReportActionType.SUSPEND_7D, "두 번째 조치");

        List<MySanctionResponse> rows = mine().items();

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().publicReason()).isEqualTo("두 번째 조치");
    }

    @Test
    @DisplayName("조치가 없으면 빈 목록")
    void emptyWhenNoAction() {
        MySanctionPageResponse page = mine();

        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("페이지를 나눠 돌려준다")
    void paginates() {
        resolve(report(), ReportActionType.WARN, "첫 번째");
        resolve(report(), ReportActionType.WARN, "두 번째");

        MySanctionPageResponse first = mySanctionService.findMySanctions(offender, 0, 1);

        assertThat(first.items()).hasSize(1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.hasNext()).isTrue();
    }

    // ---------- fixture ----------

    private MySanctionPageResponse mine() {
        return mySanctionService.findMySanctions(offender, 0, 20);
    }

    /** 소빈이 정우를 신고한다. reportId를 돌려준다 */
    private long report() {
        ReportResponse response = reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.MEMBER, String.valueOf(offender),
                ReportReasonCode.NO_SHOW, null));
        return Long.parseLong(response.id());
    }

    private void resolve(long reportId, ReportActionType action, String publicReason) {
        adminReportService.resolve(admin, reportId,
                new ResolveReportRequest(0L, action, publicReason, null));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private long saveMember(String name, MemberRole role) {
        Member member = memberRepository.save(Member.register(name));
        jdbc.update("UPDATE members SET role = ?, active_community_id = ? WHERE id = ?",
                role.name(), COMMUNITY, member.getId());
        return member.getId();
    }

    /** 자식 → 부모 순서로 지운다 */
    private void clean() {
        jdbc.update("DELETE FROM notifications");
        jdbc.update("DELETE FROM event_deliveries");
        jdbc.update("DELETE FROM outbox_events");
        jdbc.update("DELETE FROM sanctions");
        jdbc.update("DELETE FROM report_actions");
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM members");
    }
}
