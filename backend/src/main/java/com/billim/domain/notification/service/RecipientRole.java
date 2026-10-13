package com.billim.domain.notification.service;

/**
 * 사건에서 수신자가 가진 역할. 같은 사건도 역할에 따라 문구가 달라진다.
 * (소유자에게는 "요청이 왔어요", 요청자에게는 "승인됐어요")
 */
public enum RecipientRole {
    /** 물건 소유자 (빌려주는 사람) */
    OWNER,
    /** 대여 요청자 (빌리는 사람) */
    BORROWER,
    /** 신고한 회원 */
    REPORTER,
    /** 제재 대상 본인 */
    SANCTIONED
}
