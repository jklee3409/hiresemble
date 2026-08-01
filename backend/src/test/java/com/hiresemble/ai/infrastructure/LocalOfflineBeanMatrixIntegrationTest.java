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

@ActiveProfiles("local-offline")
class LocalOfflineBeanMatrixIntegrationTest extends PostgresIntegrationTest {

    @Autowired private ApplicationContext context;
    @Autowired private ChatGateway chatGateway;
    @Autowired private EmbeddingGateway embeddingGateway;
    @Autowired private WebSearchGateway searchGateway;

    @Test
    void offlineHasExactlyOneDisabledGatewayPerCapability() {
        assertThat(chatGateway).isInstanceOf(DisabledChatGateway.class);
        assertThat(embeddingGateway).isInstanceOf(DisabledEmbeddingGateway.class);
        assertThat(searchGateway).isInstanceOf(DisabledWebSearchGateway.class);
        assertThat(context.getBeansOfType(ChatGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(EmbeddingGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(WebSearchGateway.class)).hasSize(1);
        assertThat(context.getBeansOfType(SpringAiOpenAiChatGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(SpringAiOpenAiEmbeddingGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(TavilyWebSearchGateway.class)).isEmpty();
    }
}
