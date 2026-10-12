package com.billim.domain.safety.entity;

import com.billim.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * sanctions — 회원 이용 제한과 해제 이력.
 * 제재 결정은 이 도메인이 하고, 요청 차단 적용은 인증·회원 도메인이 한다. (ERD 4.7 — E 결정 / A 적용)
 */
@Entity
@Table(
        name = "sanctions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sanctions_report_action", columnNames = "report_action_id"),
        indexes = @Index(name = "idx_sanctions_member_revoked_ends",
                columnList = "member_id,revoked_at,ends_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sanction extends BaseTimeEntity {

    public static final int REVOKE_REASON_MAX = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "report_action_id", nullable = false, updatable = false)
    private Long reportActionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private SanctionKind kind;

    @Column(name = "starts_at", nullable = false, updatable = false)
    private Instant startsAt;

    /** BAN은 NULL */
    @Column(name = "ends_at", updatable = false)
    private Instant endsAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoke_reason", length = REVOKE_REASON_MAX)
    private String revokeReason;

    /**
     * 조치에서 제재를 만든다. 기간은 조치가 정한다.
     *
     * @throws IllegalArgumentException 제재를 만들지 않는 조치(WARN·DISMISS)로 호출한 경우
     */
    public static Sanction from(long memberId, long reportActionId, ReportActionType action, Instant now) {
        if (!action.createsSanction()) {
            throw new IllegalArgumentException("제재를 만들지 않는 조치입니다: " + action);
        }

        Sanction sanction = new Sanction();
        sanction.memberId = memberId;
        sanction.reportActionId = reportActionId;
        sanction.kind = action.sanctionKind();
        sanction.startsAt = now;
        sanction.endsAt = action.duration() == null ? null : now.plus(action.duration());
        return sanction;
    }

    /**
     * 유효 = 해제되지 않았고, 시작했고, 아직 끝나지 않음. (ERD 4.7)
     * 겹치는 제재는 하나라도 유효하면 회원을 제한한다.
     */
    public boolean isEffectiveAt(Instant now) {
        return revokedAt == null
                && !startsAt.isAfter(now)
                && (endsAt == null || now.isBefore(endsAt));
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 해제. 이미 해제된 제재는 최초 해제 기록을 유지한다 */
    public void revoke(long adminId, String reason, Instant now) {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = now;
        this.revokedBy = adminId;
        this.revokeReason = reason.strip();
    }
}
