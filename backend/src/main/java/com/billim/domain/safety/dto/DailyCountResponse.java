package com.billim.domain.safety.dto;

import java.time.LocalDate;

/** 일자별 건수 (API 명세 DailyCount). date는 KST 달력 날짜다 */
public record DailyCountResponse(LocalDate date, int count) {
}
