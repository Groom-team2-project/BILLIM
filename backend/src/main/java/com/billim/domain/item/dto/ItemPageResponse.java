package com.billim.domain.item.dto;

import java.util.List;

public record ItemPageResponse(List<ItemSummaryResponse> items, int page, int size,
                               long totalElements, boolean hasNext) {
}
