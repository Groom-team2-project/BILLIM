package com.billim.global.exception;

import com.billim.global.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        String requestId = newRequestId();
        log.warn("비즈니스 예외: {} - {} (requestId={})", code.name(), e.getMessage(), requestId);
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage(), requestId));
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(VersionConflictException e) {
        ErrorCode code = e.getErrorCode();
        String requestId = newRequestId();
        log.warn("버전 충돌: currentVersion={} (requestId={})", e.getCurrentVersion(), requestId);
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.versionConflict(code, e.getMessage(), requestId, e.getCurrentVersion()));
    }

    // 본문 파싱 실패·선언하지 않은 요청 필드(명세 0.1)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return invalidRequest();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalidRequest();
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        return invalidRequest();
    }

    // Spring multipart 한도 초과. 애플리케이션 검증과 같은 코드로 응답
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        ErrorCode code = ErrorCode.FILE_TOO_LARGE;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, code.getMessage(), newRequestId()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, newRequestId(), fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        String requestId = newRequestId();
        // 404·405 같은 Spring MVC 기본 예외는 500으로 덮지 않고 원래 상태 코드를 유지하되,
        // code는 명세(에러 코드.md)의 가장 가까운 공통 코드로 매핑
        if (e instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            ErrorCode code = switch (status.value()) {
                case 401 -> ErrorCode.UNAUTHENTICATED;
                case 403 -> ErrorCode.FORBIDDEN;
                case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
                case 429 -> ErrorCode.RATE_LIMITED;
                default -> status.is4xxClientError() ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
            };
            return ResponseEntity.status(status)
                    .body(ErrorResponse.of(code, code.getMessage(), requestId));
        }
        log.error("예상하지 못한 오류 (requestId={})", requestId, e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), requestId));
    }

    private ResponseEntity<ErrorResponse> invalidRequest() {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, code.getMessage(), newRequestId()));
    }

    // 요청 추적용 ID. 관측 구성 시 필터 + MDC 방식으로 대체
    private String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
