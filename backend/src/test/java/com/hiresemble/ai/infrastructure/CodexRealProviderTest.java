package com.hiresemble.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.hiresemble.ai.port.ChatGateway.ChatRequest;
import com.hiresemble.ai.port.EmbeddingGateway.EmbeddingRequest;
import com.hiresemble.ai.port.WebSearchGateway.SearchRequest;
import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/** Opt-in synthetic-only live verification. Never included in test/check/CI/E2E. */
@ActiveProfiles("local")
class CodexRealProviderTest extends PostgresIntegrationTest {

    private static final long PRICE_VERSION = 2026073101L;
    private static final Path STATE =
            Path.of(".codex-real-provider-call-summary.json").toAbsolutePath();
    private static final Path REPORT = Path.of(
            "build", "reports", "codex-real-provider", "call-summary.json");

    @Autowired private SpringAiOpenAiChatGateway chatGateway;
    @Autowired private SpringAiOpenAiEmbeddingGateway embeddingGateway;
    @Autowired private TavilyWebSearchGateway searchGateway;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void chat() throws Exception {
        gate("CHAT");
        Summary summary = reserve("CHAT", new BigDecimal("0.000100"));
        var response = chatGateway.chat(new ChatRequest(
                "openai",
                "gpt-5-mini",
                "codex-provider-v1",
                "Return exactly the required strict JSON object.",
                objectMapper.createObjectNode()
                        .put("task", "Return a strict JSON object")
                        .set("expected", objectMapper.createObjectNode().put("status", "ok")),
                "codex-provider-output-v1",
                Set.of(),
                0,
                Duration.ofSeconds(20),
                PRICE_VERSION,
                32,
                ConnectionOutput.class));
        ConnectionOutput output =
                objectMapper.readValue(response.rawJson(), ConnectionOutput.class);
        assertThat(output.status()).isEqualTo("ok");
        complete(summary, "CHAT");
    }

    @Test
    void embedding() throws Exception {
        gate("EMBEDDING");
        Summary summary = reserve("EMBEDDING", new BigDecimal("0.000001"));
        var response = embeddingGateway.embed(new EmbeddingRequest(
                "openai",
                "text-embedding-3-small",
                List.of("synthetic provider connection test"),
                1536,
                Duration.ofSeconds(20),
                PRICE_VERSION));
        assertThat(objectMapper.readTree(response.rawJson()).path("vectors"))
                .hasSize(1);
        complete(summary, "EMBEDDING");
    }

    @Test
    void search() throws Exception {
        gate("SEARCH");
        Summary summary = reserve("SEARCH", new BigDecimal("0.008000"));
        var response = searchGateway.search(new SearchRequest(
                "tavily",
                "basic",
                List.of("OpenAI official documentation"),
                "BASIC",
                1,
                Duration.ofSeconds(20),
                "OFFICIAL",
                PRICE_VERSION));
        assertThat(objectMapper.readTree(response.rawJson()).path("results").size())
                .isLessThanOrEqualTo(1);
        complete(summary, "SEARCH");
    }

    private void gate(String capability) {
        assumeTrue(
                Boolean.parseBoolean(System.getenv("CODEX_REAL_PROVIDER_TEST_ENABLED")),
                "IMPLEMENTED_NOT_LIVE_VERIFIED: real-provider gate is disabled");
        String selected = System.getProperty("codex.real-provider.capability", "ALL");
        assumeTrue("ALL".equals(selected) || capability.equals(selected));
        assumeTrue(hasSecret("AI_PROVIDER_API_KEY") && hasSecret("TAVILY_API_KEY"),
                "IMPLEMENTED_NOT_LIVE_VERIFIED: provider keys are absent");
    }

    private boolean hasSecret(String name) {
        String value = System.getenv(name);
        return value != null && !value.isBlank();
    }

    private synchronized Summary reserve(String capability, BigDecimal estimate)
            throws Exception {
        Summary current = read();
        if (current.completedCapabilities().contains(capability)) {
            throw new IllegalStateException("successful capability cannot be called again");
        }
        int capabilityCalls = switch (capability) {
            case "CHAT" -> current.chatCalls();
            case "EMBEDDING" -> current.embeddingCalls();
            case "SEARCH" -> current.searchCalls();
            default -> throw new IllegalArgumentException("unknown capability");
        };
        if (capabilityCalls >= 2 || current.totalCalls() >= 6) {
            throw new IllegalStateException("real-provider absolute call cap reached");
        }
        BigDecimal maximum = new BigDecimal(
                System.getenv().getOrDefault(
                        "CODEX_REAL_PROVIDER_TEST_MAX_COST_USD", "0.050000"));
        BigDecimal totalEstimate = current.estimatedCostUsd().add(estimate);
        if (totalEstimate.compareTo(maximum) > 0) {
            throw new IllegalStateException("real-provider estimated cost cap exceeded");
        }
        Summary reserved = new Summary(
                current.chatCalls() + ("CHAT".equals(capability) ? 1 : 0),
                current.embeddingCalls() + ("EMBEDDING".equals(capability) ? 1 : 0),
                current.searchCalls() + ("SEARCH".equals(capability) ? 1 : 0),
                current.totalCalls() + 1,
                totalEstimate,
                current.completedCapabilities());
        write(reserved);
        return reserved;
    }

    private synchronized void complete(Summary current, String capability)
            throws Exception {
        Set<String> completed = new LinkedHashSet<>(current.completedCapabilities());
        completed.add(capability);
        write(new Summary(
                current.chatCalls(),
                current.embeddingCalls(),
                current.searchCalls(),
                current.totalCalls(),
                current.estimatedCostUsd(),
                completed));
    }

    private Summary read() throws Exception {
        if (!Files.exists(STATE)) {
            return new Summary(0, 0, 0, 0, BigDecimal.ZERO.setScale(6), Set.of());
        }
        return objectMapper.readValue(Files.readString(STATE), Summary.class);
    }

    private void write(Summary summary) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(summary);
        Files.writeString(STATE, json);
        Files.createDirectories(REPORT.toAbsolutePath().getParent());
        Files.writeString(REPORT, json);
    }

    record ConnectionOutput(String status) {}

    record Summary(
            int chatCalls,
            int embeddingCalls,
            int searchCalls,
            int totalCalls,
            BigDecimal estimatedCostUsd,
            Set<String> completedCapabilities) {
        Summary {
            completedCapabilities = completedCapabilities == null
                    ? Set.of()
                    : Set.copyOf(completedCapabilities);
        }
    }
}
