package com.billim.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드. 이름·상태 코드의 원본은 docs/api/에러 코드.md 공통(global) 표.
 * 도메인별 세부 코드는 기능 개발 시 추가.
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "요청 검증에 실패했습니다. 페이지를 새로고침해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 변경되었습니다. 새로고침 후 다시 시도해 주세요."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없는 요청입니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 요청 키가 다른 내용으로 사용되었습니다."),
    MESSAGE_KEY_REUSED(HttpStatus.CONFLICT, "같은 메세지 키가 다른 내용으로 재사용되었습니다."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦습니다. 잠시 후 다시 시도해 주세요."),
    COMMUNITY_VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "동네 인증이 필요합니다."),
    MEMBER_RESTRICTED(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다."),
    ITEM_HAS_OPEN_RENTALS(HttpStatus.CONFLICT, "진행 중인 대여 요청·거래가 있어 처리할 수 없습니다."),
    MEDIA_IN_USE(HttpStatus.CONFLICT, "사용 중인 사진은 삭제하거나 제거할 수 없습니다."),
    MEDIA_NOT_ATTACHABLE(HttpStatus.CONFLICT, "연결할 수 없는 사진입니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "사진은 10MiB 이하만 올릴 수 있습니다."),
    UNSUPPORTED_IMAGE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPEG, PNG, WebP 사진만 올릴 수 있습니다."),
    INVALID_IMAGE(HttpStatus.UNPROCESSABLE_CONTENT, "읽을 수 없는 사진입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다."),   // 명세에 500 코드 없음 — 에러 코드.md 추가는 팀 논의 대기
    RETRYABLE_CONFLICT(HttpStatus.SERVICE_UNAVAILABLE, "일시적인 혼잡으로 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 연동 오류로 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
