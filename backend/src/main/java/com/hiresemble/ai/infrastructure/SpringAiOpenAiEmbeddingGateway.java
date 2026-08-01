package com.hiresemble.ai.infrastructure;

import com.hiresemble.agentrun.domain.model.UsageType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort.AiPriceUnit;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Spring AI OpenAI embedding adapter with a single bounded batch request. */
@Component
@ConditionalOnProperty(name = "hiresemble.ai.provider", havingValue = "openai")
public final class SpringAiOpenAiEmbeddingGateway implements EmbeddingGateway {

    private static final Logger log = LoggerFactory.getLogger(SpringAiOpenAiEmbeddingGateway.class);
    private static final int MAX_BATCH_ITEMS = 128;
    private static final int MAX_ITEM_CHARACTERS = 20_000;

    private final OpenAiEmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final AiPriceCatalogQueryPort priceCatalog;
    private final Duration providerTimeout;

    @Autowired
    public SpringAiOpenAiEmbeddingGateway(
            OpenAiEmbeddingModel embeddingModel,
            ObjectMapper objectMapper,
            AiPriceCatalogQueryPort priceCatalog,
            @Value("${hiresemble.ai.provider-timeout:60s}") Duration providerTimeout) {
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.priceCatalog = priceCatalog;
        this.providerTimeout = requireTimeout(providerTimeout);
    }

    SpringAiOpenAiEmbeddingGateway(
            OpenAiEmbeddingModel embeddingModel,
            ObjectMapper objectMapper,
            AiPriceCatalogQueryPort priceCatalog) {
        this(embeddingModel, objectMapper, priceCatalog, Duration.ofSeconds(60));
    }

    @Override
    public AiGatewayResponse embed(EmbeddingRequest request) {
        validate(request);
        var quote = requirePrice(request);
        var options = OpenAiEmbeddingOptions.builder()
                .model(request.productKey())
                .dimensions(request.dimension())
                .timeout(boundedTimeout(request.timeout()))
                .maxRetries(0)
                .build();
        long started = System.nanoTime();
        try {
            EmbeddingResponse response = embeddingModel.call(
                    new org.springframework.ai.embedding.EmbeddingRequest(
                            request.maskedInputs(), options));
            if (response == null || response.getResults().size() != request.maskedInputs().size()) {
                throw shape();
            }
            List<List<Double>> vectors = new ArrayList<>(response.getResults().size());
            for (int index = 0; index < response.getResults().size(); index++) {
                var item = response.getResults().get(index);
                if (item.getIndex() != null && item.getIndex() != index) {
                    throw shape();
                }
                float[] output = item.getOutput();
                if (output == null || output.length != request.dimension()) {
                    throw shape();
                }
                List<Double> vector = new ArrayList<>(output.length);
                for (float value : output) {
                    if (!Float.isFinite(value)) {
                        throw shape();
                    }
                    vector.add((double) value);
                }
                vectors.add(List.copyOf(vector));
            }
            long inputTokens = response.getMetadata() == null
                            || response.getMetadata().getUsage() == null
                    ? 0
                    : Math.max(0, response.getMetadata().getUsage().getPromptTokens());
            var usage = new AiUsage(
                    UsageType.EMBEDDING,
                    "openai",
                    request.productKey(),
                    0,
                    0,
                    0,
                    inputTokens,
                    0,
                    quote.priceVersion(),
                    quote.priceItemId(),
                    quote.costFor(inputTokens),
                    elapsed(started),
                    UUID.randomUUID());
            return new AiGatewayResponse(
                    objectMapper.writeValueAsString(Map.of("vectors", vectors)), usage);
        } catch (AiExecutionException exception) {
            throw exception;
        } catch (OpenAIServiceException exception) {
            logProviderFailure(exception);
            throw mapStatus(exception);
        } catch (OpenAIIoException exception) {
            if (isTimeout(exception)) {
                throw AiExecutionException.retryable(
                        FailureKind.TIMEOUT,
                        "AI_EMBEDDING_TIMEOUT",
                        "AI 임베딩 응답 시간이 초과되었습니다.");
            }
            throw AiExecutionException.retryable(
                    FailureKind.NETWORK,
                    "AI_EMBEDDING_NETWORK_ERROR",
                    "AI 임베딩 서비스에 연결하지 못했습니다.");
        } catch (RuntimeException exception) {
            if (isTimeout(exception)) {
                throw AiExecutionException.retryable(
                        FailureKind.TIMEOUT,
                        "AI_EMBEDDING_TIMEOUT",
                        "AI 임베딩 응답 시간이 초과되었습니다.");
            }
            throw shape();
        }
    }

    private void validate(EmbeddingRequest request) {
        if (!"openai".equals(request.providerKey()) || request.priceVersion() == null
                || request.maskedInputs().size() > MAX_BATCH_ITEMS
                || request.maskedInputs().stream().anyMatch(
                        value -> value == null || value.isBlank()
                                || value.length() > MAX_ITEM_CHARACTERS)) {
            throw configuration();
        }
    }

    private AiPriceCatalogQueryPort.AiPriceQuote requirePrice(EmbeddingRequest request) {
        try {
            return priceCatalog.requireQuote(
                    request.priceVersion(),
                    "openai",
                    request.productKey(),
                    AiPriceUnit.EMBEDDING_INPUT_TOKEN);
        } catch (RuntimeException exception) {
            throw configuration();
        }
    }

    private AiExecutionException mapStatus(OpenAIServiceException exception) {
        int status = exception.statusCode();
        if (status == 400) {
            return configuration("AI_EMBEDDING_REQUEST_REJECTED");
        }
        if (status == 401 || status == 403) {
            return configuration("AI_EMBEDDING_CREDENTIALS_REJECTED");
        }
        if (status == 404) {
            return configuration("AI_EMBEDDING_MODEL_OR_ENDPOINT_NOT_FOUND");
        }
        if (status == 429) {
            if (exception.code().filter("insufficient_quota"::equals).isPresent()) {
                return providerQuota();
            }
            return AiExecutionException.retryable(
                    FailureKind.RATE_LIMIT,
                    "AI_EMBEDDING_RATE_LIMITED",
                    "AI 임베딩 요청이 일시적으로 제한되었습니다.");
        }
        if (status >= 500) {
            return AiExecutionException.retryable(
                    FailureKind.PROVIDER_5XX,
                    "AI_EMBEDDING_PROVIDER_UNAVAILABLE",
                    "AI 임베딩 서비스를 일시적으로 사용할 수 없습니다.");
        }
        return configuration();
    }

    private void logProviderFailure(OpenAIServiceException exception) {
        String requestId = exception.headers().values("x-request-id").stream()
                .findFirst()
                .orElse("-");
        log.warn(
                "OpenAI embedding request rejected: status={}, code={}, param={}, requestId={}",
                exception.statusCode(),
                exception.code().orElse("-"),
                exception.param().orElse("-"),
                requestId);
    }

    private AiExecutionException configuration() {
        return configuration("AI_EMBEDDING_CONFIGURATION_INVALID");
    }

    private AiExecutionException configuration(String safeCode) {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                safeCode,
                "AI 임베딩 서비스 구성이 올바르지 않습니다.");
    }

    private AiExecutionException providerQuota() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_EMBEDDING_PROVIDER_QUOTA_UNAVAILABLE",
                "AI 서비스 사용 가능 한도를 확인해 주세요.");
    }

    private AiExecutionException shape() {
        return AiExecutionException.nonRetryable(
                FailureKind.CONFIGURATION,
                "AI_EMBEDDING_SHAPE_INVALID",
                "AI 임베딩 결과 구성이 올바르지 않습니다.");
    }

    private static long elapsed(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }

    private static boolean isTimeout(Throwable value) {
        for (Throwable current = value; current != null; current = current.getCause()) {
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private Duration boundedTimeout(Duration requested) {
        return requested.compareTo(providerTimeout) <= 0 ? requested : providerTimeout;
    }

    private static Duration requireTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("AI provider timeout is invalid");
        }
        return value;
    }
}
