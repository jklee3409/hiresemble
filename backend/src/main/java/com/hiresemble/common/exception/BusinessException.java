package com.hiresemble.common.exception;

import java.util.Map;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> safeContext;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Map.of(), null);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, Map.of(), cause);
    }

    public BusinessException(
            ErrorCode errorCode, Map<String, String> safeContext, Throwable cause) {
        super(errorCode.code(), cause);
        this.errorCode = errorCode;
        this.safeContext = Map.copyOf(safeContext);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, String> safeContext() {
        return safeContext;
    }
}
