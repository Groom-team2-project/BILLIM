package com.billim.domain.member.dto;

import com.billim.domain.member.entity.Member;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API 명세 UpdateProfile */
public record UpdateProfileRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotNull @Size(min = Member.DISPLAY_NAME_MIN, max = Member.DISPLAY_NAME_MAX) String displayName
) {
}
