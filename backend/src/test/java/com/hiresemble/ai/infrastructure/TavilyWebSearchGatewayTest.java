package com.hiresemble.ai.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.hiresemble.ai.execution.AiExecutionException;
import com.hiresemble.ai.port.WebSearchGateway.SearchRequest;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchBatchOutput;
import com.hiresemble.ai.workflow.InterviewPreparationWorkflow.SearchPurpose;
import com.hiresemble.ai.workflow.WorkflowRegistry.FailureKind;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TavilyWebSearchGatewayTest {

    private WireMockServer server;
    private ObjectMapper objectMapper;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void successIsBoundedAndNeverPersistsRawContentOrProviderAnswer() {
        server.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "answer":"provider synthesized answer must be ignored",
                                  "results":[
                                    {
                                      "url":"https://example.com/one",
                                      "title":"One",
                                      "content":"bounded snippet",
                                      "raw_content":"full source body must be ignored",
                                      "published_date":"2026-07-01T00:00:00Z"
                                    },
                                    {
                                      "url":"https://example.com/two",
                                      "title":"Two",
                                      "content":"second"
                                    },
                                    {
                                      "url":"https://example.com/three",
                                      "title":"Three",
                                      "content":"must be truncated by max results"
                                    }
                                  ]
                                }
                                """)));
        TavilyWebSearchGateway gateway = gateway();

        var response = gateway.search(request(2, Duration.ofSeconds(1)));
        SearchBatchOutput output =
                objectMapper.readValue(response.rawJson(), SearchBatchOutput.class);

        assertThat(output.purpose()).isEqualTo(SearchPurpose.OFFICIAL);
        assertThat(output.callSucceeded()).isTrue();
        assertThat(output.results()).hasSize(2);
        assertThat(output.results())
                .extracting(hit -> hit.sourceUrl())
                .containsExactly(
                        "https://example.com/one", "https://example.com/two");
        assertThat(response.rawJson())
                .doesNotContain(
                        "provider synthesized answer",
                        "full source body",
                        "https://example.com/three");
        assertThat(response.usage().searchUnits()).isEqualTo(1);
        assertThat(response.usage().providerKey()).isEqualTo("tavily");
        server.verify(postRequestedFor(urlEqualTo("/search"))
                .withRequestBody(equalToJson("""
                        {
                          "api_key":"fake-tavily-key",
                          "query":"Hiresemble Backend Engineer interview",
                          "search_depth":"basic",
                          "max_results":2,
                          "include_answer":false,
                          "include_raw_content":false
                        }
                        """)));
    }

    @Test
    void emptyResultsAreSuccessfulWhileMalformed429And5xxAreSafeFailures() {
        server.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\":[]}")));
        SearchBatchOutput empty = objectMapper.readValue(
                gateway().search(request(5, Duration.ofSeconds(1))).rawJson(),
                SearchBatchOutput.class);
        assertThat(empty.callSucceeded()).isTrue();
        assertThat(empty.results()).isEmpty();

        server.resetAll();
        server.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse().withStatus(200).withBody("{\"results\":{}}")));
        assertFailure("AI_SEARCH_RESPONSE_INVALID", FailureKind.STRUCTURED_OUTPUT);

        server.resetAll();
        server.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse().withStatus(429)));
        assertFailure("AI_SEARCH_RATE_LIMITED", FailureKind.RATE_LIMIT);

        server.resetAll();
        server.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse().withStatus(503)));
        assertFailure("AI_SEARCH_PROVIDER_UNAVAILABLE", FailureKind.PROVIDER_5XX);
    }

    @Test
    void timeoutIsBoundedAndConfigurationFailsClosedWithoutAKey() {
        server.stubFor(post(urlEqualTo("/search"))
                .willReturn(aResponse()
                        .withFixedDelay(250)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"results\":[]}")));
        assertThatThrownBy(() ->
                        gateway().search(request(5, Duration.ofMillis(50))))
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    assertThat(failure.safeCode()).isEqualTo("AI_SEARCH_TIMEOUT");
                    assertThat(failure.failureKind()).isEqualTo(FailureKind.TIMEOUT);
                });

        assertThatThrownBy(() -> new TavilyWebSearchGateway(
                        HttpClient.newHttpClient(),
                        objectMapper,
                        URI.create(server.baseUrl() + "/search"),
                        " ",
                        Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TAVILY_API_KEY");
    }

    private void assertFailure(String code, FailureKind kind) {
        assertThatThrownBy(() ->
                        gateway().search(request(5, Duration.ofSeconds(1))))
                .isInstanceOf(AiExecutionException.class)
                .satisfies(error -> {
                    AiExecutionException failure = (AiExecutionException) error;
                    assertThat(failure.safeCode()).isEqualTo(code);
                    assertThat(failure.failureKind()).isEqualTo(kind);
                    assertThat(failure.safeMessage())
                            .doesNotContain(
                                    "fake-tavily-key",
                                    server.baseUrl(),
                                    "Exception",
                                    "response");
                });
    }

    private TavilyWebSearchGateway gateway() {
        return new TavilyWebSearchGateway(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(1))
                        .build(),
                objectMapper,
                URI.create(server.baseUrl() + "/search"),
                "fake-tavily-key",
                Clock.systemUTC());
    }

    private SearchRequest request(int maxResults, Duration timeout) {
        return new SearchRequest(
                "tavily",
                "basic",
                List.of("Hiresemble Backend Engineer interview"),
                "BASIC",
                maxResults,
                timeout,
                "OFFICIAL");
    }
}
