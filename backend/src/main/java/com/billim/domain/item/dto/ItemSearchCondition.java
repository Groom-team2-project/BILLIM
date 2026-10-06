package com.billim.domain.item.dto;

import java.time.LocalDate;

/** 검색 조건. 값 검증은 ItemService.search에서 한다. */
public record ItemSearchCondition(
        String keyword,
        Long categoryId,
        LocalDate startDate,
        LocalDate endDate,
        Long placeId,
        ItemSort sort,
        int page,
        int size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
}
