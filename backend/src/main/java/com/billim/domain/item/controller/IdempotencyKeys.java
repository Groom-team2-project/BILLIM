package com.billim.domain.item.controller;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;

import java.util.UUID;

/**
 * POST의 Idempotency-Key 헤더 형식 검증 (API 명세 0.3: UUID 필수).
 * TODO(A·공통): idempotency_records 기반 재전송 처리는 공통 기반 구현 후 연결. 여기서는 형식만 확인한다.
 */
final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    static void require(String key) {
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 UUID여야 합니다.");
        }
    }
}
