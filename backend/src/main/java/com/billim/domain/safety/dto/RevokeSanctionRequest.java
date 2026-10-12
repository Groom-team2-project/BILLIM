package com.billim.domain.safety.dto;

import com.billim.domain.safety.entity.Sanction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 제재 해제 (API 명세 RevokeSanction). 사유는 필수다 */
public record RevokeSanctionRequest(
        @NotBlank @Size(max = Sanction.REVOKE_REASON_MAX) String reason
) {
}
