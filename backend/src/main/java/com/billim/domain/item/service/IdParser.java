package com.billim.domain.item.service;

import com.billim.global.exception.BusinessException;
import com.billim.global.exception.ErrorCode;

/** API의 십진 문자열 ID → long. 양수 signed BIGINT 범위만 허용 (API 명세 0.1) */
public final class IdParser {

    private IdParser() {
    }

    public static long parse(String raw) {
        if (raw == null || !raw.matches("[1-9][0-9]{0,18}")) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "ID 형식이 올바르지 않습니다.");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "ID 형식이 올바르지 않습니다.");
        }
    }
}
