package com.billim.domain.safety.entity;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 신고 접수 규칙과 처리 전이 (API 명세 12.4, ERD 4.7) */
class ReportTest {

    private static final long REPORTER = 1L;
    private static final long TARGET_MEMBER = 2L;
    private static final long COMMUNITY = 100L;
    private static final String SNAPSHOT = "{}";

    private Report memberReport(ReportReasonCode reasonCode, String detail) {
        return Report.open(REPORTER, ReportTargetType.MEMBER, TARGET_MEMBER, null,
                COMMUNITY, reasonCode, detail, SNAPSHOT);
    }

    @Test
    @DisplayName("접수한 신고는 미처리 상태로 시작한다")
    void newReportIsPending() {
        Report report = memberReport(ReportReasonCode.ABUSE, null);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(report.isPending()).isTrue();
    }

    @Test
    @DisplayName("대상 종류에 맞는 참조만 채운다")
    void onlyMatchingTargetReferenceIsStored() {
        Report item = Report.open(REPORTER, ReportTargetType.ITEM, TARGET_MEMBER, 55L,
                COMMUNITY, ReportReasonCode.ITEM_MISMATCH, null, SNAPSHOT);
        Report message = Report.open(REPORTER, ReportTargetType.MESSAGE, TARGET_MEMBER, 77L,
                COMMUNITY, ReportReasonCode.MONEY_REQUEST, null, SNAPSHOT);

        assertThat(item.getTargetItemId()).isEqualTo(55L);
        assertThat(item.getTargetMessageId()).isNull();
        assertThat(message.getTargetMessageId()).isEqualTo(77L);
        assertThat(message.getTargetItemId()).isNull();
    }

    @Test
    @DisplayName("응답의 targetId는 대상 종류에 맞는 참조를 내려준다")
    void targetIdFollowsTargetType() {
        assertThat(memberReport(ReportReasonCode.ABUSE, null).targetId()).isEqualTo(TARGET_MEMBER);
        assertThat(Report.open(REPORTER, ReportTargetType.ITEM, TARGET_MEMBER, 55L,
                COMMUNITY, ReportReasonCode.ITEM_MISMATCH, null, SNAPSHOT).targetId()).isEqualTo(55L);
        assertThat(Report.open(REPORTER, ReportTargetType.MESSAGE, TARGET_MEMBER, 77L,
                COMMUNITY, ReportReasonCode.ABUSE, null, SNAPSHOT).targetId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("기타 사유는 내용이 없으면 접수하지 않는다")
    void otherReasonRequiresDetail() {
        assertThatThrownBy(() -> memberReport(ReportReasonCode.OTHER, "   "))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        assertThat(memberReport(ReportReasonCode.OTHER, "금전을 요구했어요").getDetail())
                .isEqualTo("금전을 요구했어요");
    }

    @Test
    @DisplayName("기타가 아닌 사유는 내용을 비워도 접수한다")
    void otherReasonsAllowBlankDetail() {
        assertThat(memberReport(ReportReasonCode.NO_SHOW, " ").getDetail()).isNull();
    }

    @Test
    @DisplayName("자기 자신은 신고할 수 없다")
    void selfReportIsRejected() {
        assertThatThrownBy(() -> Report.open(REPORTER, ReportTargetType.MEMBER, REPORTER, null,
                COMMUNITY, ReportReasonCode.ABUSE, null, SNAPSHOT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("신고자 본인만 자기 신고로 판정한다")
    void onlyReporterOwnsReport() {
        Report report = memberReport(ReportReasonCode.ABUSE, null);

        assertThat(report.isReportedBy(REPORTER)).isTrue();
        assertThat(report.isReportedBy(TARGET_MEMBER)).isFalse();
    }

    @Test
    @DisplayName("처리하면 완료 상태가 되고, 같은 신고를 다시 처리할 수 없다")
    void resolveHappensOnce() {
        Report report = memberReport(ReportReasonCode.ABUSE, null);

        report.resolve();

        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThatThrownBy(report::resolve)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REPORT_ALREADY_RESOLVED));
    }
}
