package com.hiresemble.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.EmbeddingGateway.EmbeddingRequest;
import com.hiresemble.ai.port.WebSearchGateway.SearchRequest;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DisabledAiGatewaysTest {

    private final DisabledAiGateways gateways = new DisabledAiGateways();

    @Test
    void chatEmbeddingAndSearchFailAsSafeConfigurationWithoutNetworkFallback() {
        ObjectMapper mapper = new ObjectMapper();
        assertDisabled(() -> gateways.chat(new ChatRequest(
                "none", "none", "fixture-v1", "fixture only", mapper.createObjectNode(),
                "schema-v1", Set.of(), 0, Duration.ofSeconds(1))));
        assertDisabled(() -> gateways.embed(new EmbeddingRequest(
                "none", "none", List.of("masked fixture"), 3, Duration.ofSeconds(1))));
        assertDisabled(() -> gateways.search(new SearchRequest(
                "none", "none", List.of("fixture query"), "BASIC", 1, Duration.ofSeconds(1))));
    }

    private void assertDisabled(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    org.assertj.core.api.Assertions.assertThat(failure.failureKind())
                            .isEqualTo(FailureKind.CONFIGURATION);
                    org.assertj.core.api.Assertions.assertThat(failure.safeCode())
                            .isEqualTo("AI_PROVIDER_DISABLED");
                    org.assertj.core.api.Assertions.assertThat(failure.safeMessage())
                            .doesNotContain("key", "token", "response", "Exception");
                });
    }
}
