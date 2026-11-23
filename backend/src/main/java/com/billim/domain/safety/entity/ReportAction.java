package com.billim.domain.safety.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * report_actions — 관리자 신고 처리 이력. 한 신고에 한 건만 남는다(UNIQUE report_id).
 * public_reason은 대상·신고자에게 보이고, internal_note는 관리자 API에서만 보인다. (API 명세 13.2)
 */
@Entity
@Table(
        name = "report_actions",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_actions_report", columnNames = "report_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportAction extends BaseTimeEntity {

    public static final int PUBLIC_REASON_MAX = 500;
    public static final int INTERNAL_NOTE_MAX = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private Long reportId;

    @Column(name = "admin_id", nullable = false, updatable = false)
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReportActionType action;

    @Column(name = "public_reason", nullable = false, length = PUBLIC_REASON_MAX, updatable = false)
    private String publicReason;

    @Column(name = "internal_note", length = INTERNAL_NOTE_MAX, updatable = false)
    private String internalNote;

    public static ReportAction of(long reportId, long adminId, ReportActionType action,
                                  String publicReason, String internalNote) {
        ReportAction reportAction = new ReportAction();
        reportAction.reportId = reportId;
        reportAction.adminId = adminId;
        reportAction.action = action;
        reportAction.publicReason = publicReason.strip();
        reportAction.internalNote = internalNote == null || internalNote.isBlank()
                ? null : internalNote.strip();
        return reportAction;
    }
}
