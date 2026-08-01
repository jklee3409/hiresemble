package com.hiresemble.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class P8_5MigrationTest extends PostgresIntegrationTest {

    @Test
    void externalProviderCatalogIsExactImmutableAndUsageCallsAreIdempotent() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM ai_price_items WHERE price_version=2026073101",
                        Integer.class))
                .isEqualTo(6);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT unit_price_usd FROM ai_price_items
                        WHERE price_version=2026073101
                          AND provider_key='openai'
                          AND product_key='gpt-5-mini'
                          AND unit='CHAT_OUTPUT_TOKEN'
                        """, BigDecimal.class))
                .isEqualByComparingTo("2.000000");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT unit_price_usd FROM ai_price_items
                        WHERE price_version=2026073101
                          AND provider_key='tavily'
                          AND product_key='advanced'
                          AND unit='SEARCH_ADVANCED_REQUEST'
                        """, BigDecimal.class))
                .isEqualByComparingTo("0.016000");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        UPDATE ai_price_items SET unit_price_usd=9
                        WHERE price_version=2026073101
                        """))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*) FROM pg_indexes
                        WHERE schemaname='public'
                          AND indexname='ai_usage_records_provider_call_price_item_uk'
                        """, Integer.class))
                .isEqualTo(1);
    }

    @Test
    void activeEmbeddingPolicyUsesCanonicalProviderKey() {
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT version || ':' || provider_key || ':' || enabled
                        FROM embedding_policy_versions
                        WHERE enabled
                        """, String.class))
                .isEqualTo("2:openai:true");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM embedding_policy_versions
                        WHERE version=1 AND provider_key='OpenAI' AND NOT enabled
                        """, Integer.class))
                .isEqualTo(1);
    }
}
