package com.billim.domain.safety.dto;

import com.billim.domain.item.dto.CategoryResponse;

/**
 * 카테고리별 건수 (API 명세 CategoryCount).
 *
 * <p>카테고리 표현은 물건 도메인의 {@link CategoryResponse}를 그대로 쓴다. 같은 Category 스키마를
 * 두 벌로 두면 표시명·순서가 갈라진다. 통계는 읽기만 하고 카테고리 규칙은 물건 도메인에 남는다.
 */
public record CategoryCountResponse(CategoryResponse category, int count) {
}
