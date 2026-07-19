package com.hiresemble.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식을 확인해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
    CSRF_INVALID(HttpStatus.FORBIDDEN, "보안 토큰이 유효하지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "요청한 작업을 수행할 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    RESOURCE_VERSION_CONFLICT(HttpStatus.CONFLICT, "최신 내용을 확인한 뒤 다시 적용해 주세요."),
    RESOURCE_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 상태에서는 요청한 작업을 수행할 수 없습니다."),
    EVIDENCE_SOURCE_DELETED(HttpStatus.CONFLICT, "원본이 삭제된 근거는 변경할 수 없습니다."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 이메일입니다."),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "같은 요청이 처리 중입니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 키가 다른 요청에 사용되었습니다."),
    AGENT_RUN_RETRY_ALREADY_CREATED(HttpStatus.CONFLICT, "이 실행의 재시도가 이미 생성되었습니다."),
    RATE_OR_BUDGET_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "사용 가능한 AI 예산을 확인해 주세요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했습니다.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return name();
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
