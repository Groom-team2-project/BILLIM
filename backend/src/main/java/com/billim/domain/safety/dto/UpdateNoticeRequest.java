package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.Notice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 공지 수정·고정 (API 명세 UpdateNotice). 전체 필드 교체다 */
public record UpdateNoticeRequest(
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max = Notice.TITLE_MAX) String title,
        @NotBlank @Size(max = Notice.BODY_MAX) String body,
        @NotNull Boolean pinned
) {
}
