package com.hiresemble.ai.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiresemble.ai.port.ChatGateway;
import com.hiresemble.ai.port.EmbeddingGateway;
import com.hiresemble.ai.port.WebSearchGateway;
import com.hiresemble.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Verifies local wiring only; no gateway method is invoked and network I/O remains zero. */
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "hiresemble.ai.provider=openai",
    "hiresemble.search.provider=tavily",
    "spring.ai.model.chat=openai",
    "spring.ai.model.embedding=openai",
    "spring.ai.openai.api-key=synthetic-local-wiring-key",
    "hiresemble.search.tavily-api-key=synthetic-local-wiring-key",
    "hiresemble.document.ai-cost.estimated-cost-usd=0.300000",
    "hiresemble.document.ai-cost.price-version=2026073101",
    "hiresemble.job.ai-cost.estimated-cost-usd=0.300000",
    "hiresemble.job.ai-cost.price-version=2026073101",
    "hiresemble.cover-letter.ai-cost.generation-estimated-cost-usd=0.300000",
    "hiresemble.cover-letter.ai-cost.generation-price-version=2026073101",
    "hiresemble.cover-letter.ai-cost.verification-estimated-cost-usd=0.300000",
    "hiresemble.cover-letter.ai-cost.verification-price-version=2026073101",
    "hiresemble.interview.ai-cost.preparation-estimated-cost-usd=0.300000",
    "hiresemble.interview.ai-cost.preparation-price-version=2026073101",
    "hiresemble.interview.ai-cost.feedback-estimated-cost-usd=0.300000",
    "hiresemble.interview.ai-cost.feedback-price-version=2026073101"
})
class LocalProviderBeanMatrixIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ApplicationContext context;
    @Autowired private ChatGateway chatGateway;
    @Autowired private EmbeddingGateway embeddingGateway;
    @Autowired private WebSearchGateway searchGateway;

    @Test
    void localHasExactlyOneRealGatewayPerCapability() {
        assertThat(chatGateway).isInstanceOf(SpringAiOpenAiChatGateway.class);
        assertThat(embeddingGateway).isInstanceOf(SpringAiOpenAiEmbeddingGateway.class);
        assertThat(searchGateway).isInstanceOf(TavilyWebSearchGateway.class);
        assertThat(context.getBeansOfType(ChatGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(EmbeddingGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(WebSearchGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(DisabledChatGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(DisabledEmbeddingGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(DisabledWebSearchGateway.class)).isEmpty();
    }
}
