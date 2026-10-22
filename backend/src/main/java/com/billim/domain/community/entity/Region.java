package com.billim.domain.community.entity;

/**
 * 역지오코딩으로 얻은 동네(법정동 읍면동 단위). 좌표 한 점이 어느 읍면동에 속하는지만 담는다.
 *
 * <p>개인 좌표가 아니라 행정구역 기준 정보다. 중심 좌표는 지도 제공자가 주는 대표 좌표이며
 * 요청자의 위치가 아니다. (정책 및 상태 5 위치 인증)
 *
 * @param code           법정동 코드 앞 8자리(읍면동). 동네 동일성 판정의 기준
 * @param name           읍면동 이름. 예: 역삼동, 양평읍
 * @param displayAddress 시도 시군구 읍면동을 이어 붙인 공개 주소. 리는 넣지 않는다
 */
public record Region(
        String code,
        String name,
        String displayAddress,
        double latitude,
        double longitude
) {

    /** 저장·비교에 쓰는 길이. 읍면동까지다 */
    public static final int CODE_LENGTH = 8;

    /** 지도 제공자가 주는 법정동 코드 길이. 뒤 2자리는 리라서 버린다 */
    public static final int SOURCE_CODE_LENGTH = 10;

    /** 법정동 코드에서 읍면동까지만 남긴다. 리 단위로 쪼개면 농촌 동네가 너무 좁아진다 */
    public static String toDistrictCode(String legalCode) {
        return legalCode == null || legalCode.length() < CODE_LENGTH
                ? null
                : legalCode.substring(0, CODE_LENGTH);
    }
}
