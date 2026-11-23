package com.billim.domain.rental.service;

/**
 * 거래 당사자 판정 창구. 대여 도메인이 타 도메인에 공개하는 읽기 계약 (도메인 가이드 C06)
 *
 * <p>연동 지점 — 회원: 이웃 공개 프로필의 열람 허용과 차단 예외 (A_007)
 */
public interface RentalPartyQuery {

    /** 두 회원이 소유자·대여자로 엮인 거래 존재 여부. 거래 상태는 무관 */
    boolean hasTraded(long memberId, long otherMemberId);
}
