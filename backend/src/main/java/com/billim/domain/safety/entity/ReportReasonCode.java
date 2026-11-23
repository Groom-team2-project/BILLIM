package com.billim.domain.safety.entity;

/** reports.reason_code (정책 및 상태 5 — 금전 요구는 금지 행위이자 신고 사유) */
public enum ReportReasonCode {
    MONEY_REQUEST,
    ITEM_MISMATCH,
    NO_SHOW,
    ABUSE,
    FRAUD,
    /** 자유 서술. detail이 필수다 */
    OTHER;

    public boolean requiresDetail() {
        return this == OTHER;
    }
}
