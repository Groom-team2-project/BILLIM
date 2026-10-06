package com.billim.domain.item.dto;

import com.billim.domain.item.entity.ItemVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 공개·공개 중지 요청 (API 명세 VisibilityChange). 값은 PUBLIC·HIDDEN만 */
public record VisibilityChangeRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull ItemVisibility visibility
) {
}
