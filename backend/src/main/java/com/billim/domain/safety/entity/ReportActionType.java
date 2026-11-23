package com.billim.domain.safety.entity;

import java.time.Duration;

/**
 * report_actions.action — 관리자가 선택하는 조치. (정책 및 상태 5 제재 조치)
 * 조치가 제재 행을 만드는지, 어떤 종류·기간인지는 조치 자체가 결정한다.
 * WARN·DISMISS는 제재 행이 없다. (API 명세 13.3)
 */
public enum ReportActionType {

    /** 경고만. 이용 제한 없음 */
    WARN(null, null, "경고"),
    SUSPEND_7D(SanctionKind.SUSPEND, Duration.ofDays(7), "7일 이용 정지"),
    SUSPEND_30D(SanctionKind.SUSPEND, Duration.ofDays(30), "30일 이용 정지"),
    /** 영구 정지. 탈퇴·데이터 삭제와는 구분한다 */
    BAN(SanctionKind.BAN, null, "영구 정지"),
    /** 반려. 대상에게 아무 조치도 하지 않는다 */
    DISMISS(null, null, "반려");

    private final SanctionKind sanctionKind;
    private final Duration duration;
    private final String label;

    ReportActionType(SanctionKind sanctionKind, Duration duration, String label) {
        this.sanctionKind = sanctionKind;
        this.duration = duration;
        this.label = label;
    }

    /** 제재 행을 만드는 조치인지 */
    public boolean createsSanction() {
        return sanctionKind != null;
    }

    /** 대상 회원에게 알릴 조치인지. 반려는 대상에게 알리지 않는다 */
    public boolean notifiesTargetMember() {
        return this != DISMISS;
    }

    public SanctionKind sanctionKind() {
        return sanctionKind;
    }

    /** SUSPEND의 기간. BAN·무제재 조치는 null */
    public Duration duration() {
        return duration;
    }

    /** 알림에 쓰는 짧은 표시 문구. 관리자가 쓴 사유 원문은 쓰지 않는다 */
    public String label() {
        return label;
    }
}
