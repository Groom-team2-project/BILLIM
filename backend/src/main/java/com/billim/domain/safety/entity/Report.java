package com.billim.domain.safety.entity;

import com.billim.global.entity.BaseTimeEntity;
import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * reports — 신고 접수와 처리 상태.
 * target_member_id는 클라이언트가 보내지 않고 서버가 targetId에서 계산한다. (API 명세 12.4)
 * target_snapshot은 접수 당시의 최소 증거이며 관리자에게만 공개한다.
 */
@Entity
@Table(
        name = "reports",
        indexes = {
                @Index(name = "idx_reports_reporter_created", columnList = "reporter_id,created_at,id"),
                @Index(name = "idx_reports_community_status_created",
                        columnList = "community_id,status,created_at,id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    public static final int DETAIL_MAX = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10, updatable = false)
    private ReportTargetType targetType;

    @Column(name = "target_member_id", nullable = false, updatable = false)
    private Long targetMemberId;

    @Column(name = "target_item_id", updatable = false)
    private Long targetItemId;

    @Column(name = "target_message_id", updatable = false)
    private Long targetMessageId;

    @Column(name = "community_id", nullable = false, updatable = false)
    private Long communityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 30, updatable = false)
    private ReportReasonCode reasonCode;

    @Column(length = DETAIL_MAX, updatable = false)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status = ReportStatus.PENDING;

    /** 대상 제목·메시지 본문 등 접수 시점 스냅샷의 JSON */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_snapshot", nullable = false, updatable = false)
    private String targetSnapshot;

    @Version
    @Column(nullable = false)
    private long version;

    /**
     * 접수. 대상 해석과 접근 권한 검사는 호출 측(ReportTargetResolver)이 이미 마친 상태여야 한다.
     *
     * @param targetRef 대상 종류에 따른 참조 ID. MEMBER면 사용하지 않는다
     */
    public static Report open(long reporterId, ReportTargetType targetType, long targetMemberId,
                              Long targetRef, long communityId, ReportReasonCode reasonCode,
                              String detail, String targetSnapshot) {
        if (reporterId == targetMemberId) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "자기 자신은 신고할 수 없습니다.");
        }
        String trimmedDetail = detail == null || detail.isBlank() ? null : detail.strip();
        if (reasonCode.requiresDetail() && trimmedDetail == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "기타 사유는 내용을 입력해 주세요.");
        }

        Report report = new Report();
        report.reporterId = reporterId;
        report.targetType = targetType;
        report.targetMemberId = targetMemberId;
        report.targetItemId = targetType == ReportTargetType.ITEM ? targetRef : null;
        report.targetMessageId = targetType == ReportTargetType.MESSAGE ? targetRef : null;
        report.communityId = communityId;
        report.reasonCode = reasonCode;
        report.detail = trimmedDetail;
        report.status = ReportStatus.PENDING;
        report.targetSnapshot = targetSnapshot;
        return report;
    }

    /** API가 내려주는 targetId. 대상 종류에 맞는 참조를 고른다 */
    public long targetId() {
        return switch (targetType) {
            case MEMBER -> targetMemberId;
            case ITEM -> targetItemId;
            case MESSAGE -> targetMessageId;
        };
    }

    public boolean isReportedBy(long memberId) {
        return reporterId == memberId;
    }

    public boolean isPending() {
        return status == ReportStatus.PENDING;
    }

    /** 처리 완료로 전이한다. 이미 처리된 신고는 다시 처리하지 않는다 */
    public void resolve() {
        if (status == ReportStatus.RESOLVED) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_RESOLVED);
        }
        this.status = ReportStatus.RESOLVED;
    }
}
