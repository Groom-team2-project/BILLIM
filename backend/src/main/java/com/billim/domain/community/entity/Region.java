package com.billim.domain.community.entity;

/**
 * 역지오코딩으로 얻은 행정동. 좌표 한 점이 어느 행정동에 속하는지만 담는다.
 *
 * <p>개인 좌표가 아니라 행정동 기준 정보다. 중심 좌표는 지도 제공자가 주는 행정동 대표 좌표이며
 * 요청자의 위치가 아니다. (정책 및 상태 5 위치 인증)
 *
 * @param code           행정동 코드 10자리. 동네 동일성 판정의 기준
 * @param name           행정동 이름. 예: 역삼1동
 * @param displayAddress 시도 시군구 행정동을 이어 붙인 공개 주소
 */
public record Region(
        String code,
        String name,
        String displayAddress,
        double latitude,
        double longitude
) {

    public static final int CODE_LENGTH = 10;
}
