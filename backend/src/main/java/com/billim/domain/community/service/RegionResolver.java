package com.billim.domain.community.service;

import com.billim.domain.community.entity.Region;

import java.util.Optional;

/**
 * 좌표 → 행정동 변환. 위치 인증의 1차 판정 근거다.
 *
 * <p>외부 지도 연동이라 장애·쿼터 소진·미설정이 일상적으로 일어난다. 그래서 실패를 예외로 올리지 않고
 * 빈 값으로 돌려준다. 호출 측은 빈 값을 '행정동으로는 판정할 수 없음'으로 보고 반경 판정으로 내려간다.
 * 지도 연동이 죽어도 가입이 멈추지 않아야 하기 때문이다. (도메인 가이드 결정 '행정동 위치 인증')
 */
public interface RegionResolver {

    /** 판정할 수 없으면 {@link Optional#empty()}. 예외를 던지지 않는다 */
    Optional<Region> resolve(double latitude, double longitude);
}
