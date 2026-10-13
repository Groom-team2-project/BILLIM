package com.billim.global.exception;

import java.util.List;

/** 소유자의 승인 명령에서만 충돌 거래 식별자 제공. */
public class RentalDateConflictException extends BusinessException {
    private final List<String> conflictingRentalIds;

    public RentalDateConflictException(List<Long> rentalIds) {
        super(ErrorCode.RENTAL_DATE_CONFLICT);
        conflictingRentalIds = rentalIds.stream().map(String::valueOf).toList();
    }

    public List<String> getConflictingRentalIds() {
        return conflictingRentalIds;
    }
}
