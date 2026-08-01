package com.hiresemble.ai.infrastructure;

import java.net.URI;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort.AiPriceUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fail-closed cross-property validation; it never exposes secret values. */
@Component
public final class AiProviderActivationValidator implements SmartInitializingSingleton {

    private static final Set<String> AI_PROVIDERS = Set.of("none", "openai");
    private static final Set<String> SEARCH_PROVIDERS = Set.of("none", "tavily");

    private final Environment environment;
    private final AiPriceCatalogQueryPort priceCatalog;

    @Autowired
    public AiProviderActivationValidator(
            Environment environment, AiPriceCatalogQueryPort priceCatalog) {
        this.environment = environment;
        this.priceCatalog = priceCatalog;
    }

    AiProviderActivationValidator(Environment environment) {
        this.environment = environment;
        this.priceCatalog = null;
    }

    @Override
    public void afterSingletonsInstantiated() {
        String provider = property("hiresemble.ai.provider", "none");
        String chat = property("spring.ai.model.chat", "none");
        String embedding = property("spring.ai.model.embedding", "none");
        String search = property("hiresemble.search.provider", "none");
        boolean testProviderAllowed = Boolean.parseBoolean(
                property("hiresemble.ai.allow-test-provider", "false"));
        if ("fake".equals(provider) && testProviderAllowed) {
            if (!"none".equals(chat) || !"none".equals(embedding)) {
                throw invalid("test Fake provider must not create Spring AI models");
            }
            return;
        }
        if (!AI_PROVIDERS.contains(provider) || !AI_PROVIDERS.contains(chat)
                || !AI_PROVIDERS.contains(embedding) || !SEARCH_PROVIDERS.contains(search)) {
            throw invalid("unknown AI provider");
        }
        if (!provider.equals(chat) || !provider.equals(embedding)) {
            throw invalid("AI provider properties are inconsistent");
        }
        boolean local = Arrays.asList(environment.getActiveProfiles()).contains("local");
        boolean offline = Arrays.asList(environment.getActiveProfiles()).contains("local-offline");
        if (local && offline) {
            throw invalid("local and local-offline cannot be active together");
        }
        if (local && (!"openai".equals(provider) || !"tavily".equals(search))) {
            throw invalid("local profile requires OpenAI and Tavily");
        }
        if (offline && (!"none".equals(provider) || !"none".equals(search))) {
            throw invalid("local-offline profile must disable external providers");
        }
        if ("openai".equals(provider)) {
            requireSecret("spring.ai.openai.api-key", "AI_PROVIDER_API_KEY");
            requireOpenAiEndpoint("spring.ai.openai.base-url");
            if (!"0".equals(property("spring.ai.openai.chat.options.max-retries", "0"))
                    || !"0".equals(property(
                            "spring.ai.openai.embedding.options.max-retries", "0"))
                    || Boolean.parseBoolean(
                            property("spring.ai.openai.chat.options.store", "false"))
                    || !"none".equals(property("spring.ai.vectorstore.type", "none"))) {
                throw invalid("OpenAI retry, storage, or vector-store policy is invalid");
            }
            validateWorstCaseReservations();
            validatePriceCatalog();
        }
        if ("tavily".equals(search)) {
            requireSecret("hiresemble.search.tavily-api-key", "TAVILY_API_KEY");
            URI endpoint = uri("hiresemble.search.tavily-endpoint");
            boolean insecure = Boolean.parseBoolean(
                    property("hiresemble.search.allow-insecure-endpoint", "false"));
            if (insecure && !testProviderAllowed) {
                throw invalid("insecure Tavily endpoint is test-only");
            }
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) && !insecure) {
                throw invalid("Tavily endpoint must use HTTPS");
            }
        }
    }

    private void validatePriceCatalog() {
        if (priceCatalog == null) {
            return;
        }
        long priceVersion = Long.parseLong(
                property("hiresemble.document.ai-cost.price-version", "0"));
        Set<String> chatModels = new java.util.LinkedHashSet<>();
        for (String name : List.of(
                "hiresemble.ai.model-low-cost",
                "hiresemble.ai.model-balanced",
                "hiresemble.ai.model-high-quality")) {
            String model = property(name, "");
            if (!model.isBlank()) {
                chatModels.add(model);
            }
        }
        if (chatModels.isEmpty()) {
            throw invalid("no OpenAI chat model is configured");
        }
        try {
            for (String model : chatModels) {
                priceCatalog.requireQuote(
                        priceVersion, "openai", model, AiPriceUnit.CHAT_INPUT_TOKEN);
                priceCatalog.requireQuote(
                        priceVersion, "openai", model, AiPriceUnit.CHAT_CACHED_INPUT_TOKEN);
                priceCatalog.requireQuote(
                        priceVersion, "openai", model, AiPriceUnit.CHAT_OUTPUT_TOKEN);
            }
            priceCatalog.requireQuote(
                    priceVersion,
                    "openai",
                    property("hiresemble.ai.embedding-model", ""),
                    AiPriceUnit.EMBEDDING_INPUT_TOKEN);
            priceCatalog.requireQuote(
                    priceVersion, "tavily", "basic", AiPriceUnit.SEARCH_BASIC_REQUEST);
            priceCatalog.requireQuote(
                    priceVersion, "tavily", "advanced", AiPriceUnit.SEARCH_ADVANCED_REQUEST);
        } catch (RuntimeException exception) {
            throw invalid("required immutable AI price item is missing");
        }
    }

    private void validateWorstCaseReservations() {
        BigDecimal runMaximum = decimal("hiresemble.ai.run-max-cost-usd");
        String[] estimates = {
            "hiresemble.document.ai-cost.estimated-cost-usd",
            "hiresemble.job.ai-cost.estimated-cost-usd",
            "hiresemble.cover-letter.ai-cost.generation-estimated-cost-usd",
            "hiresemble.cover-letter.ai-cost.verification-estimated-cost-usd",
            "hiresemble.interview.ai-cost.preparation-estimated-cost-usd",
            "hiresemble.interview.ai-cost.feedback-estimated-cost-usd"
        };
        String[] versions = {
            "hiresemble.document.ai-cost.price-version",
            "hiresemble.job.ai-cost.price-version",
            "hiresemble.cover-letter.ai-cost.generation-price-version",
            "hiresemble.cover-letter.ai-cost.verification-price-version",
            "hiresemble.interview.ai-cost.preparation-price-version",
            "hiresemble.interview.ai-cost.feedback-price-version"
        };
        long[] configuredVersions = Arrays.stream(versions)
                .map(name -> property(name, "0"))
                .mapToLong(this::longValue)
                .toArray();
        if (runMaximum.signum() <= 0
                || Arrays.stream(estimates)
                        .map(this::decimal)
                        .anyMatch(value -> value.compareTo(runMaximum) < 0)
                || Arrays.stream(configuredVersions).anyMatch(value -> value < 1)
                || Arrays.stream(configuredVersions).distinct().count() != 1) {
            throw invalid("workflow reservation does not cover the absolute run cost cap");
        }
    }

    private void requireSecret(String name, String sourceName) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank() || "local-not-configured".equals(value)) {
            throw invalid(sourceName + " is required when its provider is enabled");
        }
    }

    private void requireOpenAiEndpoint(String name) {
        URI value = uri(name);
        if (!"https".equalsIgnoreCase(value.getScheme())) {
            throw invalid("OpenAI endpoint must use HTTPS");
        }
        String path = value.getPath();
        if ("api.openai.com".equalsIgnoreCase(value.getHost())
                && !("/v1".equals(path) || "/v1/".equals(path))) {
            throw invalid("official OpenAI endpoint must include the /v1 base path");
        }
    }

    private URI uri(String name) {
        try {
            URI value = URI.create(property(name, ""));
            if (value.getHost() == null || value.getUserInfo() != null
                    || value.getFragment() != null) {
                throw invalid("provider endpoint is invalid");
            }
            return value;
        } catch (IllegalArgumentException exception) {
            throw invalid("provider endpoint is invalid");
        }
    }

    private String property(String name, String fallback) {
        String value = environment.getProperty(name, fallback);
        return value == null ? fallback : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private BigDecimal decimal(String name) {
        try {
            return new BigDecimal(property(name, "0"));
        } catch (NumberFormatException exception) {
            throw invalid("AI cost property is invalid");
        }
    }

    private long longValue(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalid("AI price version is invalid");
        }
    }

    private IllegalStateException invalid(String message) {
        return new IllegalStateException("AI provider configuration invalid: " + message);
    }
}
