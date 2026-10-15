package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.Notice;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 공지 작성 (API 명세 CreateNotice) */
public record CreateNoticeRequest(
        @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}", message = "ID 형식이 올바르지 않습니다.")
        String communityId,
        @NotBlank @Size(max = Notice.TITLE_MAX) String title,
        @NotBlank @Size(max = Notice.BODY_MAX) String body,
        @NotNull Boolean pinned
) {

    public long communityIdAsLong() {
        return Long.parseLong(communityId);
    }
}
