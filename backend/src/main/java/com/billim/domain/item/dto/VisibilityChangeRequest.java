package com.billim.domain.item.dto;

import com.billim.domain.item.entity.ItemVisibility;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// PUBLIC·HIDDEN만 허용 (삭제는 삭제 API 전용)
public record VisibilityChangeRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotNull ItemVisibility visibility
) {
}
