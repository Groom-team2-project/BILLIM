package com.billim.domain.safety.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 동네 운영 통계 (API 명세 AdminStats).
 *
 * <p>누적과 기간을 구분한다. registeredItems·activeRentals·pendingReports는 조회 시점의 누적이고
 * 나머지는 from~to 기간값이다. 기간 지표는 모두 "그 기간에 생성된 대여 요청"을 같은 모집단으로 쓴다.
 * (도메인 가이드 결정 '운영 통계')
 */
public record AdminStatisticsResponse(
        String communityId,
        LocalDate from,
        LocalDate to,
        int registeredItems,
        int activeRentals,
        int newItems,
        int newMembers,
        int requestCount,
        int successfulRequestCount,
        double successRate,
        int pendingReports,
        List<DailyCountResponse> dailyRentalCounts,
        List<CategoryCountResponse> categoryCounts,
        List<PopularItemResponse> popularItems
) {
}
