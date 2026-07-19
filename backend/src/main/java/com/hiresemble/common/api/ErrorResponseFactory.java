package com.hiresemble.common.api;

import com.hiresemble.common.exception.ErrorCode;
import com.hiresemble.common.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseFactory {

    public ErrorResponseDto create(ErrorCode errorCode, HttpServletRequest request) {
        return create(errorCode, List.of(), request);
    }

    public ErrorResponseDto create(
            ErrorCode errorCode, List<FieldErrorDto> fieldErrors, HttpServletRequest request) {
        return new ErrorResponseDto(
                Instant.now(),
                errorCode.httpStatus().value(),
                errorCode.code(),
                errorCode.defaultMessage(),
                fieldErrors,
                requestId(request));
    }

    private UUID requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (value instanceof UUID requestId) {
            return requestId;
        }
        UUID generated = UUID.randomUUID();
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, generated);
        return generated;
    }
}
