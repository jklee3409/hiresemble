package com.hiresemble.ai.infrastructure;

import com.hiresemble.agentrun.application.port.AiPriceVersionPort;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort.AiPriceQuote;
import com.hiresemble.ai.port.AiPriceCatalogQueryPort.AiPriceUnit;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAiPriceCatalogRepository implements AiPriceCatalogQueryPort, AiPriceVersionPort {

    private final JdbcClient jdbcClient;

    public JdbcAiPriceCatalogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public AiPriceQuote requireQuote(
            long priceVersion, String providerKey, String productKey, AiPriceUnit unit) {
        return jdbcClient.sql("""
                        SELECT id, price_version, provider_key, product_key, unit,
                               unit_size, unit_price_usd
                        FROM ai_price_items
                        WHERE price_version = :priceVersion
                          AND provider_key = :providerKey
                          AND product_key = :productKey
                          AND unit = :unit
                        """)
                .params(Map.of(
                        "priceVersion", priceVersion,
                        "providerKey", providerKey,
                        "productKey", productKey,
                        "unit", unit.name()))
                .query((rs, ignored) -> new AiPriceQuote(
                        rs.getLong("price_version"),
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("provider_key"),
                        rs.getString("product_key"),
                        AiPriceUnit.valueOf(rs.getString("unit")),
                        rs.getLong("unit_size"),
                        rs.getBigDecimal("unit_price_usd")))
                .optional()
                .orElseThrow(() -> new IllegalStateException("required AI price item is missing"));
    }

    @Override
    public long currentPriceVersion(Instant effectiveAt) {
        if (effectiveAt == null) {
            throw new IllegalArgumentException("price catalog effective time is required");
        }
        return jdbcClient.sql("""
                        SELECT version
                        FROM ai_price_versions
                        WHERE effective_from <= :effectiveAt
                          AND (effective_to IS NULL OR effective_to > :effectiveAt)
                        ORDER BY effective_from DESC, version DESC
                        LIMIT 1
                        """)
                .param("effectiveAt", OffsetDateTime.ofInstant(effectiveAt, ZoneOffset.UTC))
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("active AI price version is missing"));
    }
}
