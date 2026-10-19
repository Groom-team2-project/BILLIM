package com.billim.domain.item.dto;

import com.billim.domain.community.dto.CommunityResponse;
import com.billim.domain.rental.dto.RentalCountStatusResponse;

import java.util.List;

// 홈 화면 조립 (API 명세 Home). 각 필드의 원천은 해당 도메인
public record HomeResponse(
        CommunityResponse community,
        int itemCount,
        List<CategoryResponse> categories,
        List<ItemSummaryResponse> latestItems,
        RentalCountStatusResponse myRentals,
        int unreadNotificationCount,
        int unreadChatCount
) {
}
