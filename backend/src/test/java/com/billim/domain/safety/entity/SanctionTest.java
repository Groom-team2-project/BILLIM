package com.billim.domain.safety.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 조치 → 제재 변환과 유효 판정 (정책 및 상태 5, ERD 4.7) */
class SanctionTest {

    private static final Instant NOW = Instant.parse("2026-10-12T01:00:00Z");
    private static final long MEMBER = 9L;
    private static final long ACTION_ID = 3L;
    private static final long ADMIN = 1L;

    @Test
    @DisplayName("경고와 반려는 제재를 만들지 않는다")
    void warnAndDismissCreateNoSanction() {
        assertThat(ReportActionType.WARN.createsSanction()).isFalse();
        assertThat(ReportActionType.DISMISS.createsSanction()).isFalse();

        assertThatThrownBy(() -> Sanction.from(MEMBER, ACTION_ID, ReportActionType.WARN, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("일시 정지는 조치가 정한 기간만큼 끝나는 시각을 가진다")
    void suspensionEndsAfterItsDuration() {
        Sanction sevenDays = Sanction.from(MEMBER, ACTION_ID, ReportActionType.SUSPEND_7D, NOW);
        Sanction thirtyDays = Sanction.from(MEMBER, ACTION_ID, ReportActionType.SUSPEND_30D, NOW);

        assertThat(sevenDays.getKind()).isEqualTo(SanctionKind.SUSPEND);
        assertThat(sevenDays.getEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(thirtyDays.getEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("영구 정지는 끝나는 시각이 없다")
    void banHasNoEnd() {
        Sanction ban = Sanction.from(MEMBER, ACTION_ID, ReportActionType.BAN, NOW);

        assertThat(ban.getKind()).isEqualTo(SanctionKind.BAN);
        assertThat(ban.getEndsAt()).isNull();
        assertThat(ban.isEffectiveAt(NOW.plus(Duration.ofDays(3650)))).isTrue();
    }

    @Test
    @DisplayName("일시 정지는 기간 안에서만 유효하다")
    void suspensionIsEffectiveOnlyWithinPeriod() {
        Sanction sanction = Sanction.from(MEMBER, ACTION_ID, ReportActionType.SUSPEND_7D, NOW);

        assertThat(sanction.isEffectiveAt(NOW)).isTrue();
        assertThat(sanction.isEffectiveAt(NOW.plus(Duration.ofDays(7)).minusSeconds(1))).isTrue();
        // 끝나는 시각은 포함하지 않는다
        assertThat(sanction.isEffectiveAt(NOW.plus(Duration.ofDays(7)))).isFalse();
        assertThat(sanction.isEffectiveAt(NOW.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("해제하면 더 이상 유효하지 않고 해제 관리자와 사유가 남는다")
    void revokedSanctionIsNotEffective() {
        Sanction sanction = Sanction.from(MEMBER, ACTION_ID, ReportActionType.BAN, NOW);

        sanction.revoke(ADMIN, " 오인 제재 ", NOW.plusSeconds(60));

        assertThat(sanction.isRevoked()).isTrue();
        assertThat(sanction.isEffectiveAt(NOW.plusSeconds(120))).isFalse();
        assertThat(sanction.getRevokedBy()).isEqualTo(ADMIN);
        assertThat(sanction.getRevokeReason()).isEqualTo("오인 제재");
    }

    @Test
    @DisplayName("이미 해제된 제재를 다시 해제해도 최초 해제 기록을 유지한다")
    void repeatedRevokeKeepsFirstRecord() {
        Sanction sanction = Sanction.from(MEMBER, ACTION_ID, ReportActionType.BAN, NOW);
        Instant firstRevoke = NOW.plusSeconds(60);
        sanction.revoke(ADMIN, "오인 제재", firstRevoke);

        sanction.revoke(ADMIN + 1, "다른 사유", NOW.plusSeconds(600));

        assertThat(sanction.getRevokedAt()).isEqualTo(firstRevoke);
        assertThat(sanction.getRevokedBy()).isEqualTo(ADMIN);
        assertThat(sanction.getRevokeReason()).isEqualTo("오인 제재");
    }

    @Test
    @DisplayName("반려만 대상에게 알리지 않는다")
    void onlyDismissSkipsTargetNotification() {
        assertThat(ReportActionType.DISMISS.notifiesTargetMember()).isFalse();
        assertThat(ReportActionType.WARN.notifiesTargetMember()).isTrue();
        assertThat(ReportActionType.SUSPEND_7D.notifiesTargetMember()).isTrue();
        assertThat(ReportActionType.BAN.notifiesTargetMember()).isTrue();
    }
}
