package com.billim.domain.community.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 현재 위치 (API 명세 VerifyLocationRequest).
 * 이 요청에서만 쓰고 DB·로그·응답에 남기지 않는다. (API 명세 E_011)
 *
 * <p>accuracyMeters 상한은 Bean Validation으로 막지 않는다. 정확도 초과는 형식 오류가 아니라
 * 422 LOCATION_INACCURATE로 응답해야 하므로 서비스에서 판정한다. (에러 코드 — community)
 */
public record VerifyLocationRequest(
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @PositiveOrZero Double accuracyMeters
) {
}
