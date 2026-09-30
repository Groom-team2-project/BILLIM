package com.billim.global.response;

import com.billim.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 모든 오류 응답의 공통 형식. (docs/api/API 명세서.md — ApiError)
 * code·message·requestId는 필수. 나머지는 조건부, null이면 응답에서 생략.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String requestId,
        List<FieldError> fieldErrors,
        Long currentVersion,
        List<String> conflictingRentalIds
) {

    public record FieldError(String field, String reason) {
    }

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(code.name(), message, requestId, null, null, null);
    }

    public static ErrorResponse of(ErrorCode code, String requestId, List<FieldError> fieldErrors) {
        return new ErrorResponse(code.name(), code.getMessage(), requestId, fieldErrors, null, null);
    }
}
