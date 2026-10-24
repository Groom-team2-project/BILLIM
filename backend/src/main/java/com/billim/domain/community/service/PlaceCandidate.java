package com.billim.domain.community.service;

import java.math.BigDecimal;

/**
 * 공용 거래 장소 후보. 지도 제공자가 알려준 공공기관 한 곳이다.
 *
 * <p>개인 좌표가 아니라 누구나 찾아갈 수 있는 공개 장소다. (정책 및 상태 5 위치 인증)
 *
 * @param name     장소 이름. 예: 역삼1동주민센터
 * @param guide    찾아가는 안내로 쓸 주소
 * @param address  경계 비교용 지번 주소. 예: 서울 강남구 역삼동 829-20
 */
public record PlaceCandidate(String name, String guide, String address,
                             BigDecimal latitude, BigDecimal longitude) {
}
