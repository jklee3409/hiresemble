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
    JOB_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "공고 분석 결과를 찾을 수 없습니다."),
    QUALITY_MODE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "이 작업에서 지원하지 않는 품질 모드입니다."),
    AI_MODEL_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "선택한 AI 모델을 사용할 수 없습니다."),
    GITHUB_URL_INVALID(HttpStatus.BAD_REQUEST, "허용된 공개 GitHub 계정 또는 저장소 URL을 입력해 주세요."),
    RESOURCE_VERSION_CONFLICT(HttpStatus.CONFLICT, "최신 내용을 확인한 뒤 다시 적용해 주세요."),
    RESOURCE_STATE_CONFLICT(HttpStatus.CONFLICT, "현재 상태에서는 요청한 작업을 수행할 수 없습니다."),
    INSUFFICIENT_JOB_DATA(HttpStatus.CONFLICT, "공고 분석에 필요한 정보가 부족합니다."),
    ACTIVE_COVER_LETTER_EXISTS(HttpStatus.CONFLICT, "이 공고에는 이미 활성 자기소개서가 있습니다."),
    COVER_LETTER_NOT_FINALIZABLE(HttpStatus.CONFLICT, "현재 자기소개서는 최종화할 수 없습니다."),
    COVER_LETTER_ARCHIVED(HttpStatus.CONFLICT, "보관된 자기소개서는 변경할 수 없습니다."),
    EVIDENCE_SOURCE_DELETED(HttpStatus.CONFLICT, "원본이 삭제된 근거는 변경할 수 없습니다."),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 등록된 이메일입니다."),
    DUPLICATE_JOB_URL(HttpStatus.CONFLICT, "이미 등록된 채용 공고 URL입니다."),
    IDEMPOTENCY_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "같은 요청이 처리 중입니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 키가 다른 요청에 사용되었습니다."),
    AGENT_RUN_RETRY_ALREADY_CREATED(HttpStatus.CONFLICT, "이 실행의 재시도가 이미 생성되었습니다."),
    GITHUB_SOURCE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 GitHub source입니다."),
    GITHUB_REPOSITORY_SELECTION_REQUIRED(HttpStatus.CONFLICT, "분석할 공개 저장소를 선택해 주세요."),
    GITHUB_SOURCE_NOT_ACCESSIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "공개 GitHub source에 접근할 수 없습니다."),
    GITHUB_SOURCE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "GitHub source 수집 한도를 초과했습니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기는 20MB 이하여야 합니다."),
    RATE_OR_BUDGET_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "사용 가능한 AI 예산을 확인해 주세요."),
    GITHUB_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "GitHub 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "외부 저장소를 일시적으로 사용할 수 없습니다."),
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
