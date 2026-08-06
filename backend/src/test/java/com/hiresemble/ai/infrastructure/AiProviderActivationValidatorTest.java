package com.hiresemble.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiProviderActivationValidatorTest {

    @Test
    void localFailsClosedWithoutProviderKeys() {
        MockEnvironment environment = localEnvironment();
        assertThatThrownBy(
                        () -> new AiProviderActivationValidator(environment)
                                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_PROVIDER_API_KEY");
    }

    @Test
    void localAcceptsConsistentRealProviderConfiguration() {
        MockEnvironment environment = localEnvironment()
                .withProperty("spring.ai.openai.api-key", "synthetic-test-key")
                .withProperty("hiresemble.search.tavily-api-key", "synthetic-test-key");
        new AiProviderActivationValidator(environment).afterSingletonsInstantiated();
    }

    @Test
    void localRejectsOfficialOpenAiEndpointWithoutV1BasePath() {
        MockEnvironment environment = localEnvironment()
                .withProperty("spring.ai.openai.base-url", "https://api.openai.com")
                .withProperty("spring.ai.openai.api-key", "synthetic-test-key")
                .withProperty("hiresemble.search.tavily-api-key", "synthetic-test-key");

        assertThatThrownBy(
                        () -> new AiProviderActivationValidator(environment)
                                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/v1");
    }

    @Test
    void offlineRejectsAnyExternalProvider() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("hiresemble.ai.provider", "openai")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "openai")
                .withProperty("hiresemble.search.provider", "none");
        environment.setActiveProfiles("local-offline");
        assertThatThrownBy(
                        () -> new AiProviderActivationValidator(environment)
                                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local-offline");
    }

    @Test
    void localRejectsTestOnlyInsecureEndpointEscapeHatch() {
        MockEnvironment environment = localEnvironment()
                .withProperty("spring.ai.openai.api-key", "synthetic-test-key")
                .withProperty("hiresemble.search.tavily-api-key", "synthetic-test-key")
                .withProperty("hiresemble.search.allow-insecure-endpoint", "true")
                .withProperty("hiresemble.search.tavily-endpoint", "http://localhost/search");
        assertThatThrownBy(
                        () -> new AiProviderActivationValidator(environment)
                                .afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test-only");
    }

    private MockEnvironment localEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("hiresemble.ai.provider", "openai")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "openai")
                .withProperty("spring.ai.vectorstore.type", "none")
                .withProperty("spring.ai.openai.base-url", "https://api.openai.com/v1")
                .withProperty("spring.ai.openai.chat.options.max-retries", "0")
                .withProperty("spring.ai.openai.embedding.options.max-retries", "0")
                .withProperty("spring.ai.openai.chat.options.store", "false")
                .withProperty("hiresemble.search.provider", "tavily")
                .withProperty(
                        "hiresemble.search.tavily-endpoint",
                        "https://api.tavily.com/search");
        environment.setActiveProfiles("local");
        return environment;
    }
}
