package com.hiresemble.common.security;

import tools.jackson.databind.ObjectMapper;
import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.common.api.ErrorResponseFactory;
import com.hiresemble.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;
    private final ErrorResponseFactory responseFactory;

    public SecurityErrorResponseWriter(
            ObjectMapper objectMapper, ErrorResponseFactory responseFactory) {
        this.objectMapper = objectMapper;
        this.responseFactory = responseFactory;
    }

    public void write(
            HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode)
            throws IOException {
        ErrorResponseDto body = responseFactory.create(errorCode, request);
        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
