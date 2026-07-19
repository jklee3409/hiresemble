package com.hiresemble.common.exception;

import com.hiresemble.common.api.ErrorResponseDto;
import com.hiresemble.common.api.ErrorResponseFactory;
import com.hiresemble.common.api.FieldErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorResponseFactory responseFactory;

    public GlobalExceptionHandler(ErrorResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponseDto> handleBusiness(
            BusinessException exception, HttpServletRequest request) {
        ErrorCode code = exception.errorCode();
        LOGGER.warn("business request failed code={} context={}", code.code(), exception.safeContext());
        return response(code, safeFieldErrors(exception), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        return response(
                ErrorCode.VALIDATION_ERROR,
                fieldErrors(exception.getBindingResult().getFieldErrors()),
                request);
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ErrorResponseDto> handleBind(
            BindException exception, HttpServletRequest request) {
        return response(
                ErrorCode.VALIDATION_ERROR,
                fieldErrors(exception.getBindingResult().getFieldErrors()),
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponseDto> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorDto> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDto(
                        lastPathElement(violation.getPropertyPath().toString()), "INVALID"))
                .distinct()
                .sorted(Comparator.comparing(FieldErrorDto::field).thenComparing(FieldErrorDto::reason))
                .limit(100)
                .toList();
        return response(ErrorCode.VALIDATION_ERROR, errors, request);
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        TypeMismatchException.class,
        HandlerMethodValidationException.class
    })
    ResponseEntity<ErrorResponseDto> handleInvalidParameter(
            Exception exception, HttpServletRequest request) {
        return response(ErrorCode.VALIDATION_ERROR, List.of(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponseDto> handleMalformedJson(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(ErrorCode.MALFORMED_REQUEST, List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponseDto> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, List.of(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponseDto> handlePayloadTooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response(ErrorCode.PAYLOAD_TOO_LARGE, List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponseDto> handleNotFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return response(ErrorCode.RESOURCE_NOT_FOUND, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponseDto> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        LOGGER.error("unexpected request failure type={}", exception.getClass().getName());
        return response(ErrorCode.INTERNAL_ERROR, List.of(), request);
    }

    private ResponseEntity<ErrorResponseDto> response(
            ErrorCode code, List<FieldErrorDto> fieldErrors, HttpServletRequest request) {
        return ResponseEntity.status(code.httpStatus())
                .body(responseFactory.create(code, fieldErrors, request));
    }

    private List<FieldErrorDto> fieldErrors(List<FieldError> errors) {
        return errors.stream()
                .map(error -> new FieldErrorDto(error.getField(), reason(error.getCode())))
                .distinct()
                .sorted(Comparator.comparing(FieldErrorDto::field).thenComparing(FieldErrorDto::reason))
                .limit(100)
                .toList();
    }

    private List<FieldErrorDto> safeFieldErrors(BusinessException exception) {
        String field = exception.safeContext().get("field");
        String reason = exception.safeContext().get("reason");
        if (field == null || reason == null) {
            return List.of();
        }
        return List.of(new FieldErrorDto(field, reason));
    }

    private String reason(String validationCode) {
        if (validationCode == null) {
            return "INVALID";
        }
        return switch (validationCode) {
            case "NotBlank", "NotEmpty", "NotNull" -> "REQUIRED";
            case "Size", "Utf8ByteLength" -> "INVALID_LENGTH";
            case "Email" -> "INVALID_FORMAT";
            case "AssertTrue" -> "MUST_BE_TRUE";
            default -> "INVALID";
        };
    }

    private String lastPathElement(String path) {
        int separator = path.lastIndexOf('.');
        return separator < 0 ? path : path.substring(separator + 1);
    }
}
