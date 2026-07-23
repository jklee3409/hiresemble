package com.hiresemble.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import com.hiresemble.auth.api.dto.LoginRequest;
import com.hiresemble.auth.api.dto.SignupRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class Utf8ByteLengthValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void signupUsesUtf8BytesAtBothBoundaries() {
        assertThat(validator.validate(signup("가가가"))).extracting("propertyPath").hasToString("[password]");
        assertThat(validator.validate(signup("가가가a"))).isEmpty();
        assertThat(validator.validate(signup("가".repeat(24)))).isEmpty();
        assertThat(validator.validate(signup("가".repeat(24) + "a")))
                .extracting("propertyPath")
                .hasToString("[password]");
    }

    @Test
    void loginAcceptsOneByteAndRejectsEmptyOrMoreThanSeventyTwoBytes() {
        assertThat(validator.validate(new LoginRequest("user@example.com", "a"))).isEmpty();
        assertThat(validator.validate(new LoginRequest("user@example.com", "")))
                .extracting("propertyPath")
                .hasToString("[password]");
        assertThat(validator.validate(new LoginRequest("user@example.com", "가".repeat(24) + "a")))
                .extracting("propertyPath")
                .hasToString("[password]");
    }

    private SignupRequest signup(String password) {
        return new SignupRequest("user@example.com", password, "User", true, true);
    }
}
