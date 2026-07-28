package com.hiresemble.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class IdempotencyLocalConfigurationTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void localProfileIsExplicitAndProvidesANonBlankDevelopmentOnlyHmacKey() throws IOException {
        PropertySource<?> application = load("application.yml");
        PropertySource<?> local = load("application-local.yml");

        assertThat(application.getProperty("spring.profiles.default")).isNull();
        assertThat(local.getProperty("hiresemble.idempotency.hmac-keys[1]"))
                .isEqualTo("hiresemble-local-only-idempotency-key-change-in-production");
    }

    private PropertySource<?> load(String resourceName) throws IOException {
        List<PropertySource<?>> sources =
                loader.load(resourceName, new ClassPathResource(resourceName));
        assertThat(sources).hasSize(1);
        return sources.getFirst();
    }
}
