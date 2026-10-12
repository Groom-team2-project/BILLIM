package com.billim.domain.safety.service;

import com.billim.domain.item.entity.Category;
import com.billim.domain.item.entity.Item;
import com.billim.domain.item.repository.CategoryRepository;
import com.billim.domain.item.repository.ItemRepository;
import com.billim.domain.member.entity.Member;
import com.billim.domain.member.entity.MemberRole;
import com.billim.domain.member.repository.MemberRepository;
import com.billim.domain.notification.entity.EventType;
import com.billim.domain.notification.entity.Notification;
import com.billim.domain.notification.entity.NotificationTargetType;
import com.billim.domain.notification.entity.OutboxEvent;
import com.billim.domain.notification.repository.NotificationRepository;
import com.billim.domain.notification.repository.OutboxEventRepository;
import com.billim.domain.notification.service.OutboxDispatcher;
import com.billim.domain.safety.dto.CreateReportRequest;
import com.billim.domain.safety.dto.ReportResponse;
import com.billim.domain.safety.dto.ResolveReportRequest;
import com.billim.domain.safety.entity.Report;
import com.billim.domain.safety.entity.ReportActionType;
import com.billim.domain.safety.entity.ReportReasonCode;
import com.billim.domain.safety.entity.ReportStatus;
import com.billim.domain.safety.entity.ReportTargetType;
import com.billim.domain.safety.entity.Sanction;
import com.billim.domain.safety.entity.SanctionKind;
import com.billim.domain.safety.repository.ReportActionRepository;
import com.billim.domain.safety.repository.ReportRepository;
import com.billim.domain.safety.repository.SanctionRepository;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import com.billim.global.exception.VersionConflictException;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 신고 접수 → 관리자 처리·제재 → 알림까지의 통합 동작 (API 명세 12.4 ~ 13.5).
 *
 * <p>처리·제재·사건이 한 트랜잭션에서 커밋되는지 확인해야 하므로 테스트를 트랜잭션으로 감싸지 않고
 * 직접 정리한다. 사건 소비는 주기 실행을 끄고 디스패처를 직접 호출한다.
 */
@Import(ReportFlowDbTest.MySql.class)
@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.kakao.client-id=test-client-id",
        "spring.security.oauth2.client.registration.kakao.client-secret=test-client-secret",
        "billim.notification.outbox.enabled=false"
})
class ReportFlowDbTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class MySql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    private static final long COMMUNITY = 100L;
    private static final long OTHER_COMMUNITY = 200L;

    @Autowired ReportService reportService;
    @Autowired AdminReportService adminReportService;
    @Autowired SanctionService sanctionService;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired ReportRepository reportRepository;
    @Autowired ReportActionRepository reportActionRepository;
    @Autowired SanctionRepository sanctionRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired OutboxEventRepository outboxEventRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbc;

    private long reporter;
    private long offender;
    private long admin;
    private long offenderItemId;

    @BeforeEach
    void setUp() {
        clean();
        reporter = saveMember("소빈", MemberRole.USER, COMMUNITY);
        offender = saveMember("정우", MemberRole.USER, COMMUNITY);
        admin = saveMember("운영자", MemberRole.ADMIN, COMMUNITY);
        offenderItemId = saveItem(offender, COMMUNITY, "전동드릴");
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
        jdbc.update("DELETE FROM sanctions");
        jdbc.update("DELETE FROM report_actions");
        jdbc.update("DELETE FROM reports");
        jdbc.update("DELETE FROM item_images");
        jdbc.update("DELETE FROM items");
        jdbc.update("DELETE FROM members");
    }

    // ---------- 접수 ----------

    @Test
    @DisplayName("물건을 신고하면 대상 회원은 소유자로, 맥락은 물건의 동네로 기록된다")
    void itemReportResolvesOwnerAndCommunity() {
        ReportResponse response = reportService.create(reporter,
                new CreateReportRequest(ReportTargetType.ITEM, String.valueOf(offenderItemId),
                        ReportReasonCode.MONEY_REQUEST, null));

        Report saved = reportRepository.findById(Long.valueOf(response.id())).orElseThrow();
        assertThat(saved.getTargetMemberId()).isEqualTo(offender);
        assertThat(saved.getTargetItemId()).isEqualTo(offenderItemId);
        assertThat(saved.getCommunityId()).isEqualTo(COMMUNITY);
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(response.targetMember().displayName()).isEqualTo("정우");
        // 처리 전에는 조치 정보가 없다
        assertThat(response.action()).isNull();
        assertThat(response.publicReason()).isNull();
    }

    @Test
    @DisplayName("같은 대상·사유의 미처리 신고가 있으면 중복으로 거부한다")
    void duplicatePendingReportIsRejected() {
        CreateReportRequest request = new CreateReportRequest(
                ReportTargetType.ITEM, String.valueOf(offenderItemId), ReportReasonCode.MONEY_REQUEST, null);
        reportService.create(reporter, request);

        assertThatThrownBy(() -> reportService.create(reporter, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_REPORT));

        assertThat(reportRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("사유가 다르면 같은 대상에도 다시 신고할 수 있다")
    void differentReasonIsNotDuplicate() {
        reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.ITEM, String.valueOf(offenderItemId), ReportReasonCode.MONEY_REQUEST, null));
        reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.ITEM, String.valueOf(offenderItemId), ReportReasonCode.ITEM_MISMATCH, null));

        assertThat(reportRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("처리된 신고는 중복 기준에서 빠져 같은 대상·사유로 다시 신고할 수 있다")
    void resolvedReportDoesNotBlockNewReport() {
        CreateReportRequest request = new CreateReportRequest(
                ReportTargetType.ITEM, String.valueOf(offenderItemId), ReportReasonCode.MONEY_REQUEST, null);
        ReportResponse first = reportService.create(reporter, request);
        resolve(Long.valueOf(first.id()), first.version(), ReportActionType.WARN);

        reportService.create(reporter, request);

        assertThat(reportRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("다른 동네 물건은 볼 수 없으므로 신고도 404다")
    void itemInAnotherCommunityIsNotFound() {
        long strangerItemId = saveItem(saveMember("지민", MemberRole.USER, OTHER_COMMUNITY),
                OTHER_COMMUNITY, "캠핑 의자");

        assertThatThrownBy(() -> reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.ITEM, String.valueOf(strangerItemId), ReportReasonCode.ABUSE, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("자기 물건은 신고할 수 없다")
    void ownItemCannotBeReported() {
        long myItemId = saveItem(reporter, COMMUNITY, "내 물건");

        assertThatThrownBy(() -> reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.ITEM, String.valueOf(myItemId), ReportReasonCode.ABUSE, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("동네 인증 전에는 회원을 신고할 수 없다")
    void memberReportRequiresActiveCommunity() {
        long unverified = saveMember("미인증", MemberRole.USER, null);

        assertThatThrownBy(() -> reportService.create(unverified, new CreateReportRequest(
                ReportTargetType.MEMBER, String.valueOf(offender), ReportReasonCode.ABUSE, null)))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMMUNITY_VERIFICATION_REQUIRED));
    }

    @Test
    @DisplayName("내 신고 목록과 상세는 본인 것만 보여주고, 타인의 신고는 404다")
    void myReportsAreScopedToReporter() {
        ReportResponse mine = reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.MEMBER, String.valueOf(offender), ReportReasonCode.ABUSE, null));
        long otherReporter = saveMember("현섭", MemberRole.USER, COMMUNITY);
        reportService.create(otherReporter, new CreateReportRequest(
                ReportTargetType.MEMBER, String.valueOf(offender), ReportReasonCode.FRAUD, null));

        assertThat(reportService.findMyReports(reporter, 0, 20).items()).hasSize(1);
        assertThat(reportService.findMyReport(reporter, Long.valueOf(mine.id())).id()).isEqualTo(mine.id());
        assertThatThrownBy(() -> reportService.findMyReport(otherReporter, Long.valueOf(mine.id())))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // ---------- 처리·제재 ----------

    @Test
    @DisplayName("일시 정지로 처리하면 처리 이력·제재·사건이 함께 남는다")
    void suspensionCreatesActionSanctionAndEvent() {
        ReportResponse report = openReport(ReportReasonCode.MONEY_REQUEST);

        adminReportService.resolve(admin, Long.valueOf(report.id()),
                new ResolveReportRequest(report.version(), ReportActionType.SUSPEND_7D,
                        "대여료를 요구했습니다", "증거 캡처 보관"));

        assertThat(reportRepository.findById(Long.valueOf(report.id())).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.RESOLVED);
        assertThat(reportActionRepository.findByReportId(Long.valueOf(report.id()))).isPresent();

        List<Sanction> sanctions = sanctionRepository.findAll();
        assertThat(sanctions).hasSize(1);
        assertThat(sanctions.getFirst().getKind()).isEqualTo(SanctionKind.SUSPEND);
        assertThat(sanctions.getFirst().getMemberId()).isEqualTo(offender);
        assertThat(sanctionService.isRestricted(offender)).isTrue();

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getEventType()).isEqualTo(EventType.REPORT_RESOLVED);
    }

    @Test
    @DisplayName("경고와 반려는 제재 행을 만들지 않는다")
    void warnAndDismissCreateNoSanction() {
        ReportResponse warned = openReport(ReportReasonCode.ABUSE);
        resolve(Long.valueOf(warned.id()), warned.version(), ReportActionType.WARN);

        ReportResponse dismissed = openReport(ReportReasonCode.NO_SHOW);
        resolve(Long.valueOf(dismissed.id()), dismissed.version(), ReportActionType.DISMISS);

        assertThat(sanctionRepository.findAll()).isEmpty();
        assertThat(sanctionService.isRestricted(offender)).isFalse();
    }

    @Test
    @DisplayName("처리 알림은 신고자는 신고 결과로, 제재 대상은 자기 회원 정보로 이동한다")
    void resolvedNotificationsMoveToDifferentTargets() {
        ReportResponse report = openReport(ReportReasonCode.MONEY_REQUEST);
        long reportId = Long.valueOf(report.id());
        resolve(reportId, report.version(), ReportActionType.SUSPEND_30D);

        dispatcher.dispatchBatch();

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(2);

        Notification toReporter = notificationOf(notifications, reporter);
        assertThat(toReporter.getTargetType()).isEqualTo(NotificationTargetType.REPORT);
        assertThat(toReporter.getTargetId()).isEqualTo(reportId);
        assertThat(toReporter.getTitle()).doesNotContain("정우");

        Notification toOffender = notificationOf(notifications, offender);
        assertThat(toOffender.getTargetType()).isEqualTo(NotificationTargetType.MEMBER_SELF);
        assertThat(toOffender.getTargetId()).isEqualTo(offender);
        assertThat(toOffender.getTitle()).contains("30일 이용 정지");
        // 신고자가 누구인지, 관리자가 쓴 사유 원문은 알리지 않는다
        assertThat(toOffender.getTitle()).doesNotContain("소빈").doesNotContain("대여료를 요구했습니다");
    }

    @Test
    @DisplayName("반려는 신고자에게만 알린다")
    void dismissNotifiesReporterOnly() {
        ReportResponse report = openReport(ReportReasonCode.NO_SHOW);
        resolve(Long.valueOf(report.id()), report.version(), ReportActionType.DISMISS);

        dispatcher.dispatchBatch();

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getRecipientId()).isEqualTo(reporter);
    }

    @Test
    @DisplayName("같은 신고를 두 번 처리할 수 없다")
    void reportIsResolvedOnce() {
        ReportResponse report = openReport(ReportReasonCode.ABUSE);
        long reportId = Long.valueOf(report.id());
        resolve(reportId, report.version(), ReportActionType.WARN);

        assertThatThrownBy(() -> resolve(reportId, report.version(), ReportActionType.BAN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_ALREADY_RESOLVED));

        assertThat(reportActionRepository.findAll()).hasSize(1);
        assertThat(sanctionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("오래된 version으로 처리하면 현재 version과 함께 충돌을 알린다")
    void staleVersionIsRejected() {
        ReportResponse report = openReport(ReportReasonCode.ABUSE);

        assertThatThrownBy(() -> resolve(Long.valueOf(report.id()), report.version() + 5,
                ReportActionType.WARN))
                .isInstanceOfSatisfying(VersionConflictException.class,
                        e -> assertThat(e.getCurrentVersion()).isEqualTo(report.version()));

        assertThat(reportActionRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("관리자 회원은 제재할 수 없고 처리 흔적도 남지 않는다")
    void adminMemberCannotBeSanctioned() {
        ReportResponse report = reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.MEMBER, String.valueOf(admin), ReportReasonCode.ABUSE, null));

        assertThatThrownBy(() -> resolve(Long.valueOf(report.id()), report.version(), ReportActionType.BAN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(reportActionRepository.findAll()).isEmpty();
        assertThat(reportRepository.findById(Long.valueOf(report.id())).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.PENDING);
    }

    @Test
    @DisplayName("처리가 실패하면 제재도 사건도 남지 않는다")
    void failedResolveLeavesNothing() {
        ReportResponse report = openReport(ReportReasonCode.ABUSE);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            adminReportService.resolve(admin, Long.valueOf(report.id()),
                    new ResolveReportRequest(report.version(), ReportActionType.BAN, "영구 정지", null));
            throw new IllegalStateException("처리 중 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(reportActionRepository.findAll()).isEmpty();
        assertThat(sanctionRepository.findAll()).isEmpty();
        assertThat(outboxEventRepository.findAll()).isEmpty();
        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("관리자 상세에는 신고자와 내부 메모가 보이고, 신고자 응답에는 내부 메모가 없다")
    void internalNoteIsAdminOnly() {
        ReportResponse report = openReport(ReportReasonCode.MONEY_REQUEST);
        long reportId = Long.valueOf(report.id());
        adminReportService.resolve(admin, reportId, new ResolveReportRequest(
                report.version(), ReportActionType.WARN, "대여료를 요구했습니다", "증거 캡처 보관"));

        var adminView = adminReportService.findReport(reportId);
        assertThat(adminView.reporter().displayName()).isEqualTo("소빈");
        assertThat(adminView.internalNote()).isEqualTo("증거 캡처 보관");
        assertThat(adminView.communityId()).isEqualTo(String.valueOf(COMMUNITY));

        ReportResponse reporterView = reportService.findMyReport(reporter, reportId);
        assertThat(reporterView.publicReason()).isEqualTo("대여료를 요구했습니다");
        assertThat(reporterView.action()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("운영 목록은 상태로 걸러진다")
    void adminListFiltersByStatus() {
        ReportResponse resolved = openReport(ReportReasonCode.ABUSE);
        resolve(Long.valueOf(resolved.id()), resolved.version(), ReportActionType.WARN);
        openReport(ReportReasonCode.NO_SHOW);

        assertThat(adminReportService.findReports(ReportStatus.PENDING, null, 0, 20).items()).hasSize(1);
        assertThat(adminReportService.findReports(ReportStatus.RESOLVED, null, 0, 20).items()).hasSize(1);
        assertThat(adminReportService.findReports(null, null, 0, 20).items()).hasSize(2);
        assertThat(adminReportService.findReports(null, OTHER_COMMUNITY, 0, 20).items()).isEmpty();
    }

    // ---------- 제재 해제 ----------

    @Test
    @DisplayName("제재를 해제하면 제한이 풀리고 이력에는 남는다")
    void revokedSanctionLiftsRestriction() {
        ReportResponse report = openReport(ReportReasonCode.MONEY_REQUEST);
        resolve(Long.valueOf(report.id()), report.version(), ReportActionType.BAN);
        long sanctionId = sanctionRepository.findAll().getFirst().getId();

        var revoked = sanctionService.revoke(admin, sanctionId, "오인 제재");

        assertThat(revoked.revokedAt()).isNotNull();
        assertThat(revoked.isEffective()).isFalse();
        assertThat(sanctionService.isRestricted(offender)).isFalse();
        assertThat(sanctionService.findSanctions(offender, 0, 20).items()).hasSize(1);
    }

    @Test
    @DisplayName("해제를 반복해도 최초 해제 기록이 유지된다")
    void repeatedRevokeIsSafe() {
        ReportResponse report = openReport(ReportReasonCode.MONEY_REQUEST);
        resolve(Long.valueOf(report.id()), report.version(), ReportActionType.SUSPEND_7D);
        long sanctionId = sanctionRepository.findAll().getFirst().getId();

        sanctionService.revoke(admin, sanctionId, "오인 제재");
        Sanction afterFirst = sanctionRepository.findById(sanctionId).orElseThrow();

        var second = sanctionService.revoke(admin, sanctionId, "다른 사유");

        // 두 번째 호출은 아무것도 바꾸지 않고 최초 기록을 그대로 돌려준다.
        // (DATETIME(6) 저장값과 비교한다. 방금 쓴 응답은 나노초까지 들고 있어 정밀도가 다르다)
        assertThat(second.revokedAt()).isEqualTo(afterFirst.getRevokedAt());
        Sanction afterSecond = sanctionRepository.findById(sanctionId).orElseThrow();
        assertThat(afterSecond.getRevokedAt()).isEqualTo(afterFirst.getRevokedAt());
        assertThat(afterSecond.getRevokeReason()).isEqualTo("오인 제재");
        assertThat(afterSecond.getRevokedBy()).isEqualTo(admin);
    }

    @Test
    @DisplayName("다른 유효 제재가 남아 있으면 하나를 해제해도 계속 제한된다")
    void otherEffectiveSanctionKeepsRestriction() {
        ReportResponse first = openReport(ReportReasonCode.MONEY_REQUEST);
        resolve(Long.valueOf(first.id()), first.version(), ReportActionType.SUSPEND_7D);
        ReportResponse second = openReport(ReportReasonCode.FRAUD);
        resolve(Long.valueOf(second.id()), second.version(), ReportActionType.BAN);

        sanctionService.revoke(admin, sanctionRepository.findAll().getFirst().getId(), "오인 제재");

        assertThat(sanctionService.isRestricted(offender)).isTrue();
    }

    // ---------- fixtures ----------

    private ReportResponse openReport(ReportReasonCode reasonCode) {
        return reportService.create(reporter, new CreateReportRequest(
                ReportTargetType.ITEM, String.valueOf(offenderItemId), reasonCode, null));
    }

    private void resolve(long reportId, long version, ReportActionType action) {
        adminReportService.resolve(admin, reportId,
                new ResolveReportRequest(version, action, "대여료를 요구했습니다", null));
    }

    private static Notification notificationOf(List<Notification> notifications, long recipientId) {
        return notifications.stream()
                .filter(notification -> notification.getRecipientId() == recipientId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("수신자 알림이 없습니다: " + recipientId));
    }

    private long saveMember(String name, MemberRole role, Long communityId) {
        Member member = memberRepository.save(Member.register(name));
        // role·active_community_id는 도메인 변경 메서드가 없어 테스트에서 직접 세팅한다
        jdbc.update("UPDATE members SET role = ?, active_community_id = ? WHERE id = ?",
                role.name(), communityId, member.getId());
        return member.getId();
    }

    private long saveItem(long ownerId, long communityId, String title) {
        Category category = categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc().getFirst();
        Item item = Item.create(ownerId, communityId, category.getId(), 10L, title, null,
                LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 30),
                java.time.Instant.parse("2026-10-12T01:00:00Z"));
        return itemRepository.save(item).getId();
    }
}
