package com.billim.domain.rental.dto;

public record TrustMetricsResponse(int completedCount, int lentCount, int returnPromiseKept,
                                   int returnPromiseTotal, String basis) {
}
