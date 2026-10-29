package com.billim.domain.safety.dto;

/** 인기 물건 (API 명세 PopularItem). returnedCount는 기간 내 반납 완료 건수다 */
public record PopularItemResponse(String itemId, String title, int returnedCount) {
}
