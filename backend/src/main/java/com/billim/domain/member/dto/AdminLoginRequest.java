package com.billim.domain.member.dto;

import jakarta.validation.constraints.NotBlank;

/** 관리자 전용 로그인 요청 */
public record AdminLoginRequest(@NotBlank String loginId, @NotBlank String password) {
}
