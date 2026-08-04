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
    void signupRequiresTenCharactersAndLetterNumberSpecialCompositionWithinSeventyTwoBytes() {
        assertThat(validator.validate(signup("Abcdef1!x")))
                .extracting("propertyPath")
                .hasToString("[password]");
        assertThat(validator.validate(signup("Abcdefg1!x"))).isEmpty();
        assertThat(validator.validate(signup("가".repeat(23) + "A1!"))).isEmpty();
        assertThat(validator.validate(signup("가".repeat(23) + "A1!a")))
                .extracting("propertyPath")
                .hasToString("[password]");

        assertThat(validator.validate(signup("abcdefghij")))
                .extracting("propertyPath")
                .hasToString("[password]");
        assertThat(validator.validate(signup("abcdefgh1j")))
                .extracting("propertyPath")
                .hasToString("[password]");
        assertThat(validator.validate(signup("12345678!0")))
                .extracting("propertyPath")
                .hasToString("[password]");
        assertThat(validator.validate(signup("abcdefg1\u0000x")))
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
