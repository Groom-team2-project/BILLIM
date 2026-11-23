package com.billim.domain.community.dto;

/**
 * 동네 선택에서 고른 상위 단계 (API 명세 E_009, E_073).
 *
 * <p>위에서 아래로 채운다. 아래 단계만 채우고 위를 비우면 범위가 정해지지 않으므로
 * 서비스가 400으로 막는다. 비어 있는 값은 "전체"다.
 */
public record RegionScope(String sido, String sigungu, String gu, String dong) {

    /** 아무 단계도 고르지 않은 범위. 활성 동네 전체다 */
    public static final RegionScope ALL = new RegionScope("", "", "", "");

    /** null·공백을 빈 문자열로 맞춘다. 쿼리에서 빈 문자열이 "조건 없음"이기 때문이다 */
    public static RegionScope of(String sido, String sigungu, String gu, String dong) {
        return new RegionScope(blankToEmpty(sido), blankToEmpty(sigungu),
                blankToEmpty(gu), blankToEmpty(dong));
    }

    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.strip();
    }

    public boolean hasSido() {
        return !sido.isEmpty();
    }

    public boolean hasSigungu() {
        return !sigungu.isEmpty();
    }

    public boolean hasGu() {
        return !gu.isEmpty();
    }

    public boolean hasDong() {
        return !dong.isEmpty();
    }
}
