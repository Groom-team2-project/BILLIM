package com.billim.domain.community.service;

import java.util.Arrays;

/**
 * 공개 주소를 동네 선택 단계로 쪼갠 값 (API 명세 E_073).
 *
 * <p>공개 주소는 카카오 역지오코딩의 {@code region_1depth + region_2depth + region_3depth_h}를
 * 공백으로 이은 값이다({@link KakaoRegionResolver}). {@code region_2depth}가 "수원시 영통구"처럼
 * 두 단어인 시가 있어 토큰이 3개 또는 4개다. 세종특별자치시는 시·군·구가 없어 2개다.
 *
 * <p>읍·면·동은 동네 이름이 아니라 <b>공개 주소의 마지막 토큰</b>에서 읽는다. 행정동 동네는
 * 이름이 곧 행정동이지만, 아파트 단지처럼 경계가 다른 동네는 이름이 단지명이다
 * (도메인 및 DB 설계 communities 절). 그래서 둘 다 담는 공개 주소를 기준으로 삼는다.
 *
 * <p>그러므로 운영 seed로 넣는 동네도 공개 주소를 {@code 시도 시군구[ 구] 읍면동} 형식으로
 * 맞춰야 단계 선택에 나타난다. 뒤에 번지 같은 토큰을 더 붙이면 그 토큰이 읍·면·동으로 읽힌다.
 *
 * @param sido    시·도. 예: 서울특별시
 * @param sigungu 시·군·구. 예: 강남구, 수원시. 세종특별자치시는 빈 문자열
 * @param gu      구가 있는 시의 구. 예: 영통구. 없으면 빈 문자열
 * @param dong    읍·면·동. 예: 역삼1동
 */
public record RegionPath(String sido, String sigungu, String gu, String dong) {

    /** 공개 주소가 단계로 쪼개지지 않을 때 쓰는 값. 단계 조회에서 제외된다 */
    private static final RegionPath EMPTY = new RegionPath("", "", "", "");

    /**
     * 공개 주소를 단계로 쪼갠다. 토큰이 2개 미만이면 모두 빈 문자열이다.
     *
     * <p>토큰이 5개 이상이면 가운데를 모두 구로 본다. 그런 주소는 지금 없지만, 떨어뜨리는
     * 쪽보다 시·도와 읍·면·동을 살리는 쪽이 선택 화면에 쓸모가 있다.
     */
    public static RegionPath of(String displayAddress) {
        if (displayAddress == null || displayAddress.isBlank()) {
            return EMPTY;
        }
        String[] tokens = displayAddress.strip().split("\\s+");
        return switch (tokens.length) {
            case 0, 1 -> EMPTY;
            case 2 -> new RegionPath(tokens[0], "", "", tokens[1]);
            case 3 -> new RegionPath(tokens[0], tokens[1], "", tokens[2]);
            default -> new RegionPath(tokens[0], tokens[1],
                    String.join(" ", Arrays.asList(tokens).subList(2, tokens.length - 1)),
                    tokens[tokens.length - 1]);
        };
    }

    /** 단계로 쪼개지지 않은 주소. 단계 조회 결과에 담지 않는다 */
    public boolean isEmpty() {
        return sido.isEmpty() || dong.isEmpty();
    }
}
