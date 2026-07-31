package com.hiresemble.ai.infrastructure;

import com.hiresemble.agentrun.domain.model.UsageType;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.AiGatewayResponse;
import com.hiresemble.ai.port.AiUsage;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchBatchOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchHit;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchPurpose;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Explicitly enabled Tavily adapter. The default application still uses network-disabled search. */
@Component
@Primary
@ConditionalOnProperty(
        name = "hiresemble.search.provider",
        havingValue = "tavily")
public final class TavilyWebSearchGateway implements WebSearchGateway {

    private static final String OUTPUT_SCHEMA = "web-search-results-v1";
    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final Clock clock;

    public TavilyWebSearchGateway(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${hiresemble.search.tavily-api-key:}") String apiKey,
            @Value("${hiresemble.search.tavily-endpoint:https://api.tavily.com/search}")
                    URI endpoint) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                objectMapper,
                endpoint,
                apiKey,
                clock);
    }

    TavilyWebSearchGateway(
            HttpClient client,
            ObjectMapper objectMapper,
            URI endpoint,
            String apiKey,
            Clock clock) {
        this.client = Objects.requireNonNull(client);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.endpoint = requireEndpoint(endpoint);
        this.apiKey = requireApiKey(apiKey);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AiGatewayResponse search(SearchRequest request) {
        SearchPurpose purpose;
        try {
            purpose = SearchPurpose.valueOf(request.purpose());
        } catch (IllegalArgumentException exception) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "AI_SEARCH_REQUEST_INVALID",
                    "면접 조사 검색 요청 구성이 올바르지 않습니다.");
        }
        long started = clock.millis();
        List<SearchHit> results = new ArrayList<>();
        int successfulCalls = 0;
        AiExecutionException lastFailure = null;
        for (String query : request.queries()) {
            try {
                results.addAll(searchOne(query, request));
                successfulCalls++;
            } catch (AiExecutionException failure) {
                lastFailure = failure;
            }
        }
        if (successfulCalls == 0) {
            throw lastFailure == null
                    ? providerFailure()
                    : lastFailure;
        }
        var output = new SearchBatchOutput(
                OUTPUT_SCHEMA, purpose, true, null, results);
        var usage = new AiUsage(
                UsageType.SEARCH,
                "tavily",
                request.researchQuality().toLowerCase(java.util.Locale.ROOT),
                0,
                0,
                0,
                0,
                request.queries().size(),
                null,
                null,
                BigDecimal.ZERO.setScale(6),
                Math.max(0, clock.millis() - started));
        try {
            return new AiGatewayResponse(
                    objectMapper.writeValueAsString(output), usage);
        } catch (RuntimeException exception) {
            throw AiExecutionException.nonRetryable(
                    FailureKind.CONFIGURATION,
                    "AI_SEARCH_RESPONSE_INVALID",
                    "면접 조사 검색 결과를 처리하지 못했습니다.");
        }
    }

    private List<SearchHit> searchOne(String query, SearchRequest request) {
        JsonNode payload = objectMapper.createObjectNode()
                .put("api_key", apiKey)
                .put("query", query)
                .put(
                        "search_depth",
                        "ADVANCED".equals(request.researchQuality())
                                ? "advanced"
                                : "basic")
                .put("max_results", request.maxResultsPerQuery())
                .put("include_answer", false)
                .put("include_raw_content", false);
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(request.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw AiExecutionException.retryable(
                        FailureKind.RATE_LIMIT,
                        "AI_SEARCH_RATE_LIMITED",
                        "면접 조사 검색 요청이 일시적으로 제한되었습니다.");
            }
            if (response.statusCode() >= 500) {
                throw providerFailure();
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.CONFIGURATION,
                        "AI_SEARCH_REQUEST_REJECTED",
                        "면접 조사 검색 요청 구성을 확인해 주세요.");
            }
            if (response.body().length() > MAX_RESPONSE_BYTES) {
                throw AiExecutionException.nonRetryable(
                        FailureKind.SAFETY,
                        "AI_SEARCH_RESPONSE_TOO_LARGE",
                        "면접 조사 검색 결과가 허용 범위를 초과했습니다.");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.path("results");
            if (!values.isArray()) {
                throw malformed();
            }
            List<SearchHit> hits = new ArrayList<>();
            int rank = 0;
            for (JsonNode value : values) {
                if (rank >= request.maxResultsPerQuery()) {
                    break;
                }
                String url = value.path("url").asText();
                if (url.isBlank()) {
                    continue;
                }
                rank++;
                hits.add(new SearchHit(
                        query,
                        bounded(url, 2_000),
                        nullable(value.path("title").asText(), 500),
                        nullable(value.path("content").asText(), 2_000),
                        nullable(value.path("published_date").asText(), 100),
                        rank));
            }
            return List.copyOf(hits);
        } catch (java.net.http.HttpTimeoutException exception) {
            throw AiExecutionException.retryable(
                    FailureKind.TIMEOUT,
                    "AI_SEARCH_TIMEOUT",
                    "면접 조사 검색 응답 시간이 초과되었습니다.");
        } catch (java.io.IOException exception) {
            throw AiExecutionException.retryable(
                    FailureKind.NETWORK,
                    "AI_SEARCH_NETWORK_ERROR",
                    "면접 조사 검색 서비스에 연결하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw AiExecutionException.retryable(
                    FailureKind.NETWORK,
                    "AI_SEARCH_INTERRUPTED",
                    "면접 조사 검색 요청이 중단되었습니다.");
        } catch (AiExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed();
        }
    }

    private AiExecutionException providerFailure() {
        return AiExecutionException.retryable(
                FailureKind.PROVIDER_5XX,
                "AI_SEARCH_PROVIDER_UNAVAILABLE",
                "면접 조사 검색 서비스를 일시적으로 사용할 수 없습니다.");
    }

    private AiExecutionException malformed() {
        return AiExecutionException.retryable(
                FailureKind.STRUCTURED_OUTPUT,
                "AI_SEARCH_RESPONSE_INVALID",
                "면접 조사 검색 결과 형식이 올바르지 않습니다.");
    }

    private static URI requireEndpoint(URI value) {
        if (value == null
                || (!"https".equalsIgnoreCase(value.getScheme())
                        && !"http".equalsIgnoreCase(value.getScheme()))
                || value.getHost() == null
                || value.getUserInfo() != null
                || value.getFragment() != null) {
            throw new IllegalStateException("Tavily endpoint is invalid");
        }
        return value;
    }

    private static String requireApiKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "TAVILY_API_KEY is required when Tavily search is enabled");
        }
        return value;
    }

    private String bounded(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String nullable(String value, int maxLength) {
        return value == null || value.isBlank() ? null : bounded(value, maxLength);
    }
}
