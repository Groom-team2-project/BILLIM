package com.billim.domain.community.dto;

import java.util.List;

/**
 * 동네 선택 단계 옵션 (API 명세 E_073).
 *
 * @param level 요청한 단계
 * @param items 그 단계에서 고를 수 있는 값. 가나다순, 중복 없음.
 *              해당 단계가 없는 지역(구가 없는 시, 시·군·구가 없는 세종)은 빈 배열
 */
public record RegionOptionsResponse(RegionLevel level, List<String> items) {
}
