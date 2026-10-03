package com.billim.domain.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** 물건 정보·사진 전체 수정 요청 (API 명세 UpdateItem). description 생략은 제거 */
public record UpdateItemRequest(
        @NotBlank @Size(max = 100) String title,
        @Size(max = 3000) String description,
        @NotBlank String categoryId,
        @NotBlank String placeId,
        @NotNull LocalDate availableStartDate,
        @NotNull LocalDate availableEndDate,
        @NotNull @Size(min = 1, max = 5) List<@NotBlank String> imageIds,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
