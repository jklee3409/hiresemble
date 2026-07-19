package com.hiresemble.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void codesAreUniqueAndHaveErrorStatuses() {
        Set<String> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::code)
                .collect(Collectors.toSet());

        assertThat(codes).hasSize(ErrorCode.values().length);
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::httpStatus))
                .allMatch(status -> status.is4xxClientError() || status.is5xxServerError());
        assertThat(Arrays.stream(ErrorCode.values()))
                .allMatch(code -> code.code().equals(code.name()))
                .allMatch(code -> !code.defaultMessage().isBlank());
    }
}
