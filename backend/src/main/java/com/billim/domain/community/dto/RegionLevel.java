package com.billim.domain.community.dto;

/**
 * 동네 선택 단계 (API 명세 E_073).
 *
 * <p>위에서 아래로 좁힌다. 상위 단계를 고르면 다음 단계 옵션이 그 안으로 한정된다.
 * {@link #GU}는 "수원시 영통구"처럼 시 아래 구가 있는 지역에만 있고, 없으면 빈 목록이다.
 */
public enum RegionLevel {

    /** 시·도 */
    SIDO,

    /** 시·군·구 */
    SIGUNGU,

    /** 구가 있는 시의 구 */
    GU,

    /** 읍·면·동 */
    DONG
}
