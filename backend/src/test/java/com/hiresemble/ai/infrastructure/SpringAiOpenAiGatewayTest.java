package com.hiresemble.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hiresemble.ai.port.AiPriceCatalogQueryPort;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort.AiPriceQuote;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.EmbeddingGateway.EmbeddingRequest;
import com.hiresemble.ai.prompt.PromptRegistry;
import com.hiresemble.ai.prompt.PromptRegistry.PromptDefinition;
import com.hiresemble.ai.prompt.PromptRegistry.PromptKey;
import com.hiresemble.ai.validation.OpenAiStrictSchemaCompatibilityValidator;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaRegistry;
import com.hiresemble.ai.validation.StrictStructuredOutputSchemaGenerator;
import com.hiresemble.agentrun.domain.model.WorkflowType;
import com.openai.core.http.Headers;
import com.openai.core.JsonValue;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import tools.jackson.databind.ObjectMapper;

class SpringAiOpenAiGatewayTest {

    private static final long PRICE_VERSION = 2026073101L;

    @Test
    void chatUsesStrictRequestOptionsAndSplitsPriceItems() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        Usage nativeUsage = mock(Usage.class);
        when(nativeUsage.getPromptTokens()).thenReturn(100);
        when(nativeUsage.getCacheReadInputTokens()).thenReturn(20L);
        when(nativeUsage.getCompletionTokens()).thenReturn(10);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("{\"status\":\"ok\"}"))),
                ChatResponseMetadata.builder().usage(nativeUsage).build()));
        var gateway = new SpringAiOpenAiChatGateway(
                model, new ObjectMapper(), prices(), schemas());

        var response = gateway.chat(new ChatRequest(
                "openai",
                "gpt-5-mini",
                "test-v1",
                "Return the required object.",
                new ObjectMapper().createObjectNode().put("value", "untrusted"),
                "test-output-v1",
                Set.of(),
                0,
                Duration.ofSeconds(3),
                PRICE_VERSION,
                32,
                TestOutput.class));

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("gpt-5-mini");
        assertThat(options.getMaxRetries()).isZero();
        assertThat(options.getMaxCompletionTokens()).isEqualTo(32);
        assertThat(options.getN()).isEqualTo(1);
        assertThat(options.getStore()).isFalse();
        assertThat(options.getParallelToolCalls()).isNull();
        assertThat(options.getToolChoice()).isNull();
        assertThat(options.getOutputSchema()).contains("\"additionalProperties\" : false");
        assertThat(prompt.getValue().getSystemMessage().getText())
                .isEqualTo("Return the required object.");
        assertThat(prompt.getValue().getUserMessage().getText())
                .contains("untrusted external data", "\"value\":\"untrusted\"");
        assertThat(response.usages()).hasSize(3);
        assertThat(response.usages()).extracting("inputUnits", Long.class)
                .containsExactly(80L, 0L, 0L);
        assertThat(response.usages()).extracting("cachedInputUnits", Long.class)
                .containsExactly(0L, 20L, 0L);
        assertThat(response.usages()).extracting("outputUnits", Long.class)
                .containsExactly(0L, 0L, 10L);
        assertThat(response.usages()).extracting("costUsd", BigDecimal.class)
                .containsExactly(
                        new BigDecimal("0.000020"),
                        new BigDecimal("0.000001"),
                        new BigDecimal("0.000020"));
    }

    @Test
    void springAiSendsTheValidatedSchemaAsStrictNativeResponseFormat() {
        OpenAIClient client = mock(OpenAIClient.class);
        OpenAIClientAsync asyncClient = mock(OpenAIClientAsync.class);
        ChatService chatService = mock(ChatService.class);
        ChatCompletionService completionService = mock(ChatCompletionService.class);
        when(client.chat()).thenReturn(chatService);
        when(chatService.completions()).thenReturn(completionService);
        var message = ChatCompletionMessage.builder()
                .content("{\"status\":\"ok\"}")
                .refusal(Optional.empty())
                .role(JsonValue.from("assistant"))
                .annotations(List.of())
                .toolCalls(List.of())
                .build();
        var choice = ChatCompletion.Choice.builder()
                .index(0)
                .finishReason(ChatCompletion.Choice.FinishReason.STOP)
                .logprobs(Optional.empty())
                .message(message)
                .build();
        var completion = ChatCompletion.builder()
                .id("chatcmpl_test")
                .choices(List.of(choice))
                .created(1)
                .model("gpt-5-mini")
                .object_(JsonValue.from("chat.completion"))
                .build();
        when(completionService.create(any(ChatCompletionCreateParams.class)))
                .thenReturn(completion);
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(asyncClient)
                .options(OpenAiChatOptions.builder()
                        .model("gpt-5-mini")
                        .maxRetries(0)
                        .build())
                .build();
        var registry = schemas();
        var gateway = new SpringAiOpenAiChatGateway(
                model, new ObjectMapper(), prices(), registry);

        gateway.chat(request());

        ArgumentCaptor<ChatCompletionCreateParams> request =
                ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(completionService).create(request.capture());
        var jsonSchema = request.getValue().responseFormat().orElseThrow()
                .asJsonSchema().jsonSchema();
        assertThat(jsonSchema.name()).isEqualTo(
                StrictStructuredOutputSchemaRegistry.PROVIDER_SCHEMA_NAME);
        assertThat(jsonSchema.strict()).contains(true);
        assertThat(jsonSchema.schema()).isPresent();
        assertThat(jsonSchema.schema().orElseThrow().toString())
                .contains("additionalProperties", "properties", "status");
        assertThat(request.getValue().store()).contains(false);
        assertThat(request.getValue().tools()).isEmpty();
        assertThat(request.getValue().toolChoice()).isEmpty();
    }

    @Test
    void embeddingPreservesOrderDimensionAndOneRequestUsage() {
        OpenAiEmbeddingModel model = mock(OpenAiEmbeddingModel.class);
        Usage nativeUsage = mock(Usage.class);
        when(nativeUsage.getPromptTokens()).thenReturn(25);
        var metadata = new EmbeddingResponseMetadata(
                "text-embedding-3-small", nativeUsage);
        when(model.call(any(org.springframework.ai.embedding.EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(
                        List.of(
                                new Embedding(new float[] {0.1f, 0.2f}, 0),
                                new Embedding(new float[] {0.3f, 0.4f}, 1)),
                        metadata));
        var gateway = new SpringAiOpenAiEmbeddingGateway(
                model, new ObjectMapper(), prices());

        var response = gateway.embed(new EmbeddingRequest(
                "openai",
                "text-embedding-3-small",
                List.of("masked one", "masked two"),
                2,
                Duration.ofSeconds(3),
                PRICE_VERSION));

        ArgumentCaptor<org.springframework.ai.embedding.EmbeddingRequest> request =
                ArgumentCaptor.forClass(org.springframework.ai.embedding.EmbeddingRequest.class);
        verify(model).call(request.capture());
        OpenAiEmbeddingOptions options =
                (OpenAiEmbeddingOptions) request.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("text-embedding-3-small");
        assertThat(options.getDimensions()).isEqualTo(2);
        assertThat(options.getMaxRetries()).isZero();
        assertThat(response.rawJson()).contains("\"vectors\"");
        assertThat(response.usages()).singleElement()
                .satisfies(usage -> {
                    assertThat(usage.embeddingUnits()).isEqualTo(25);
                    assertThat(usage.costUsd()).isEqualByComparingTo("0.000001");
                });
    }

    @Test
    void chatMapsProviderBadRequestWithoutExposingProviderPayload() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        OpenAIServiceException failure = mock(OpenAIServiceException.class);
        when(failure.statusCode()).thenReturn(400);
        when(failure.code()).thenReturn(Optional.of("unsupported_parameter"));
        when(failure.param()).thenReturn(Optional.of("tool_choice"));
        when(failure.headers()).thenReturn(
                Headers.builder().put("x-request-id", "req_test").build());
        when(model.call(any(Prompt.class))).thenThrow(failure);
        var gateway = new SpringAiOpenAiChatGateway(
                model, new ObjectMapper(), prices(), schemas());

        assertThatThrownBy(() -> gateway.chat(new ChatRequest(
                        "openai",
                        "gpt-5-mini",
                        "test-v1",
                        "Return the required object.",
                        new ObjectMapper().createObjectNode().put("value", "untrusted"),
                        "test-output-v1",
                        Set.of(),
                        0,
                        Duration.ofSeconds(3),
                        PRICE_VERSION,
                        32,
                        TestOutput.class)))
                .isInstanceOfSatisfying(AiExecutionException.class, exception -> {
                    assertThat(exception.safeCode()).isEqualTo("AI_CHAT_REQUEST_REJECTED");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void chatDistinguishesStructuredSchemaRejectionFromResponseValidation() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        OpenAIServiceException failure = mock(OpenAIServiceException.class);
        when(failure.statusCode()).thenReturn(400);
        when(failure.code()).thenReturn(Optional.of("invalid_json_schema"));
        when(failure.param()).thenReturn(Optional.of("response_format.json_schema.schema"));
        when(failure.headers()).thenReturn(
                Headers.builder().put("x-request-id", "req_schema_test").build());
        when(model.call(any(Prompt.class))).thenThrow(failure);
        var gateway = new SpringAiOpenAiChatGateway(
                model, new ObjectMapper(), prices(), schemas());

        assertThatThrownBy(() -> gateway.chat(new ChatRequest(
                        "openai",
                        "gpt-5-mini",
                        "test-v1",
                        "Return the required object.",
                        new ObjectMapper().createObjectNode().put("value", "untrusted"),
                        "test-output-v1",
                        Set.of(),
                        0,
                        Duration.ofSeconds(3),
                        PRICE_VERSION,
                        32,
                        TestOutput.class)))
                .isInstanceOfSatisfying(AiExecutionException.class, exception -> {
                    assertThat(exception.safeCode())
                            .isEqualTo("AI_CHAT_STRUCTURED_SCHEMA_REJECTED");
                    assertThat(exception.failureKind())
                            .isEqualTo(com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind.STRUCTURED_SCHEMA);
                    assertThat(exception.retryable()).isFalse();
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void chatTreatsInsufficientProviderQuotaAsNonRetryableConfiguration() {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        OpenAIServiceException failure = mock(OpenAIServiceException.class);
        when(failure.statusCode()).thenReturn(429);
        when(failure.code()).thenReturn(Optional.of("insufficient_quota"));
        when(failure.param()).thenReturn(Optional.empty());
        when(failure.headers()).thenReturn(Headers.builder().build());
        when(model.call(any(Prompt.class))).thenThrow(failure);
        var gateway = new SpringAiOpenAiChatGateway(
                model, new ObjectMapper(), prices(), schemas());

        assertThatThrownBy(() -> gateway.chat(new ChatRequest(
                        "openai",
                        "gpt-5-mini",
                        "test-v1",
                        "Return the required object.",
                        new ObjectMapper().createObjectNode().put("value", "untrusted"),
                        "test-output-v1",
                        Set.of(),
                        0,
                        Duration.ofSeconds(3),
                        PRICE_VERSION,
                        32,
                        TestOutput.class)))
                .isInstanceOfSatisfying(AiExecutionException.class, exception -> {
                    assertThat(exception.safeCode())
                            .isEqualTo("AI_CHAT_PROVIDER_QUOTA_UNAVAILABLE");
                    assertThat(exception.retryable()).isFalse();
                });
    }

    private AiPriceCatalogQueryPort prices() {
        return (version, provider, product, unit) -> new AiPriceQuote(
                version,
                UUID.nameUUIDFromBytes((product + unit).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                provider,
                product,
                unit,
                unit.name().startsWith("SEARCH_") ? 1 : 1_000_000,
                switch (unit) {
                    case CHAT_INPUT_TOKEN -> new BigDecimal("0.250000");
                    case CHAT_CACHED_INPUT_TOKEN -> new BigDecimal("0.025000");
                    case CHAT_OUTPUT_TOKEN -> new BigDecimal("2.000000");
                    case EMBEDDING_INPUT_TOKEN -> new BigDecimal("0.020000");
                    case SEARCH_BASIC_REQUEST -> new BigDecimal("0.008000");
                    case SEARCH_ADVANCED_REQUEST -> new BigDecimal("0.016000");
                });
    }

    private StrictStructuredOutputSchemaRegistry schemas() {
        ObjectMapper objectMapper = new ObjectMapper();
        PromptDefinition definition = new PromptDefinition(
                new PromptKey(WorkflowType.DOCUMENT_INGESTION, "test-workflow-v1", "TEST_CHAT"),
                "test-v1",
                tools.jackson.databind.JsonNode.class,
                TestOutput.class,
                "test-output-v1",
                Set.of(),
                100,
                32,
                1,
                "Return the required object.");
        return new StrictStructuredOutputSchemaRegistry(
                new PromptRegistry(List.of(definition)),
                new StrictStructuredOutputSchemaGenerator(objectMapper),
                new OpenAiStrictSchemaCompatibilityValidator(objectMapper));
    }

    private ChatRequest request() {
        return new ChatRequest(
                "openai",
                "gpt-5-mini",
                "test-v1",
                "Return the required object.",
                new ObjectMapper().createObjectNode().put("value", "untrusted"),
                "test-output-v1",
                Set.of(),
                0,
                Duration.ofSeconds(3),
                PRICE_VERSION,
                32,
                TestOutput.class);
    }

    private record TestOutput(String status) {}
}
