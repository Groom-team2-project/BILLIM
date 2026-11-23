package com.billim.domain.notification.entity;

/** notifications.target_type. 알림을 눌렀을 때 이동할 대상 (API 명세 Notification) */
public enum NotificationTargetType {
    RENTAL,
    REPORT,
    /** 제재 대상 본인. 신고자 정보는 노출하지 않는다 */
    MEMBER_SELF
}
