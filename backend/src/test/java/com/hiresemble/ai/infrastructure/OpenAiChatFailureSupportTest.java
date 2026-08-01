package com.hiresemble.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.openai.core.http.Headers;
import com.openai.errors.OpenAIServiceException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class OpenAiChatFailureSupportTest {

    @Test
    void textAndImageCapabilitiesUseTheSameProviderStatusSemantics() {
        List<ProviderCase> cases = List.of(
                new ProviderCase(400, "invalid_json_schema", "response_format.json_schema.schema",
                        FailureKind.STRUCTURED_SCHEMA, false, "STRUCTURED_SCHEMA_REJECTED"),
                new ProviderCase(400, "invalid_request_error", "messages",
                        FailureKind.CONFIGURATION, false, "REQUEST_REJECTED"),
                new ProviderCase(401, "invalid_api_key", null,
                        FailureKind.CONFIGURATION, false, "CREDENTIALS_REJECTED"),
                new ProviderCase(403, "forbidden", null,
                        FailureKind.CONFIGURATION, false, "CREDENTIALS_REJECTED"),
                new ProviderCase(404, "model_not_found", "model",
                        FailureKind.CONFIGURATION, false, "MODEL_OR_ENDPOINT_NOT_FOUND"),
                new ProviderCase(429, "insufficient_quota", null,
                        FailureKind.CONFIGURATION, false, "PROVIDER_QUOTA_UNAVAILABLE"),
                new ProviderCase(429, "rate_limit_exceeded", null,
                        FailureKind.RATE_LIMIT, true, "RATE_LIMITED"),
                new ProviderCase(500, "server_error", null,
                        FailureKind.PROVIDER_5XX, true, "PROVIDER_UNAVAILABLE"),
                new ProviderCase(503, "server_error", null,
                        FailureKind.PROVIDER_5XX, true, "PROVIDER_UNAVAILABLE"));

        for (ProviderCase fixture : cases) {
            OpenAIServiceException provider = providerFailure(fixture);
            AiExecutionException chat = OpenAiChatFailureSupport.mapServiceFailure(
                    provider, OpenAiChatFailureSupport.Capability.CHAT);
            AiExecutionException image = OpenAiChatFailureSupport.mapServiceFailure(
                    provider, OpenAiChatFailureSupport.Capability.IMAGE_TEXT);

            assertThat(chat.failureKind()).isEqualTo(fixture.kind());
            assertThat(image.failureKind()).isEqualTo(fixture.kind());
            assertThat(chat.retryable()).isEqualTo(fixture.retryable());
            assertThat(image.retryable()).isEqualTo(fixture.retryable());
            assertThat(chat.safeCode()).endsWith(fixture.codeSuffix());
            assertThat(image.safeCode()).endsWith(fixture.codeSuffix());
        }
    }

    @Test
    void responseFailuresKeepUsageAndUseCapabilitySpecificSafeCodes() {
        AiUsage usage = mock(AiUsage.class);
        List<AiUsage> usages = List.of(usage);
        List<ResponseCase> cases = List.of(
                new ResponseCase(response("partial", "length"), FailureKind.STRUCTURED_OUTPUT,
                        "OUTPUT_TRUNCATED"),
                new ResponseCase(response("blocked", "content_filter"), FailureKind.SAFETY,
                        "SAFETY_BLOCKED"),
                new ResponseCase(response("partial", "tool_calls"), FailureKind.STRUCTURED_OUTPUT,
                        "COMPLETION_INCOMPLETE"),
                new ResponseCase(response(" ", "stop"), FailureKind.STRUCTURED_OUTPUT,
                        "OUTPUT_INVALID"),
                new ResponseCase(new ChatResponse(List.of()), FailureKind.STRUCTURED_OUTPUT,
                        "COMPLETION_INCOMPLETE"),
                new ResponseCase(new ChatResponse(List.of(
                        generation("one", "stop"), generation("two", "stop"))),
                        FailureKind.STRUCTURED_OUTPUT, "COMPLETION_INCOMPLETE"),
                new ResponseCase(toolCallResponse(), FailureKind.STRUCTURED_OUTPUT,
                        "COMPLETION_INCOMPLETE"),
                new ResponseCase(refusalResponse(), FailureKind.SAFETY,
                        "SAFETY_BLOCKED"));

        for (ResponseCase fixture : cases) {
            assertThatThrownBy(() -> OpenAiChatFailureSupport.requireCompletedText(
                            fixture.response(), usages,
                            OpenAiChatFailureSupport.Capability.IMAGE_TEXT))
                    .isInstanceOfSatisfying(AiExecutionException.class, failure -> {
                        assertThat(failure.failureKind()).isEqualTo(fixture.kind());
                        assertThat(failure.safeCode()).endsWith(fixture.codeSuffix());
                        assertThat(failure.incurredUsages()).containsExactly(usage);
                    });
        }
    }

    @Test
    void timeoutAndNetworkRemainRetryableButDoNotInventUsage() {
        AiUsage usage = mock(AiUsage.class);
        AiExecutionException timeout = OpenAiChatFailureSupport.timeout(
                OpenAiChatFailureSupport.Capability.IMAGE_TEXT, List.of(usage));
        AiExecutionException network = OpenAiChatFailureSupport.network(
                OpenAiChatFailureSupport.Capability.IMAGE_TEXT);

        assertThat(timeout.failureKind()).isEqualTo(FailureKind.TIMEOUT);
        assertThat(timeout.retryable()).isTrue();
        assertThat(timeout.incurredUsages()).containsExactly(usage);
        assertThat(network.failureKind()).isEqualTo(FailureKind.NETWORK);
        assertThat(network.retryable()).isTrue();
        assertThat(network.incurredUsages()).isEmpty();
    }

    private OpenAIServiceException providerFailure(ProviderCase fixture) {
        OpenAIServiceException failure = mock(OpenAIServiceException.class);
        when(failure.statusCode()).thenReturn(fixture.status());
        when(failure.code()).thenReturn(Optional.ofNullable(fixture.code()));
        when(failure.param()).thenReturn(Optional.ofNullable(fixture.param()));
        when(failure.headers()).thenReturn(Headers.builder().build());
        return failure;
    }

    private ChatResponse response(String text, String finishReason) {
        return new ChatResponse(List.of(generation(text, finishReason)));
    }

    private Generation generation(String text, String finishReason) {
        return new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason(finishReason).build());
    }

    private ChatResponse toolCallResponse() {
        AssistantMessage message = AssistantMessage.builder()
                .content("ignored")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call_1", "function", "unsafe", "{}")))
                .build();
        return new ChatResponse(List.of(new Generation(
                message, ChatGenerationMetadata.builder().finishReason("stop").build())));
    }

    private ChatResponse refusalResponse() {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .properties(java.util.Map.of("refusal", "provider refusal details"))
                .build();
        return new ChatResponse(List.of(new Generation(
                message, ChatGenerationMetadata.builder().finishReason("stop").build())));
    }

    private record ProviderCase(
            int status, String code, String param, FailureKind kind,
            boolean retryable, String codeSuffix) {}

    private record ResponseCase(
            ChatResponse response, FailureKind kind, String codeSuffix) {}
}
