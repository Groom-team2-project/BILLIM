package com.billim.domain.item.controller;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

// POST의 Idempotency-Key 헤더 형식 검증 (API 명세 0.3: UUID 필수). 대소문자 차이를 없앤 키 반환
final class IdempotencyKeys {

    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private IdempotencyKeys() {
    }

    static String require(String key) {
        if (key == null || !UUID.matcher(key).matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 UUID여야 합니다.");
        }
        return key.toLowerCase(Locale.ROOT);
    }
}
