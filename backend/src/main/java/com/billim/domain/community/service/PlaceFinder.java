package com.billim.domain.community.service;

import java.util.List;

/**
 * 좌표 주변의 공용 거래 장소 후보를 찾는다.
 *
 * <p>외부 지도 연동이라 장애·쿼터 소진·미설정이 일상적으로 일어난다. 그래서 실패를 예외로
 * 올리지 않고 빈 목록으로 돌려준다. 장소를 못 채워도 동네 조회 자체는 성공해야 한다.
 * (도메인 가이드 결정 '공용 장소 자동 보충')
 */
public interface PlaceFinder {

    /**
     * 경계 안의 공용 장소 후보를 가까운 순으로 찾는다.
     *
     * @param boundary 동네 경계 기준. 시군구가 다른 후보는 제외하고, 같은 동네 후보를 먼저 쓴다
     * @return 찾을 수 없으면 빈 목록. 예외를 던지지 않는다
     */
    List<PlaceCandidate> findNear(double latitude, double longitude,
                                  PlaceBoundary boundary, int limit);
}
