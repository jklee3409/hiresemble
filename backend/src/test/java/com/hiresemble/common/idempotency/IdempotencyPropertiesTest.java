package com.hiresemble.common.idempotency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IdempotencyPropertiesTest {

    @Test
    void startupFailsClosedWhenTheActiveHashKeyIsBlank() {
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setHmacKeys(Map.of(1, ""));

        assertThatIllegalStateException()
                .isThrownBy(properties::afterPropertiesSet)
                .withMessageContaining("idempotency HMAC key must be configured");
    }

    @Test
    void startupAcceptsAConfiguredActiveHashKey() {
        IdempotencyProperties properties = new IdempotencyProperties();
        properties.setHmacKeys(Map.of(1, "configured-environment-secret"));

        assertThatCode(properties::afterPropertiesSet).doesNotThrowAnyException();
    }
}
