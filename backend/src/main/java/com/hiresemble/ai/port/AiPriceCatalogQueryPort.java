package com.hiresemble.ai.port;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Exact immutable price lookup captured by an Agent Run's price version. */
public interface AiPriceCatalogQueryPort {

    AiPriceQuote requireQuote(
            long priceVersion, String providerKey, String productKey, AiPriceUnit unit);

    enum AiPriceUnit {
        CHAT_INPUT_TOKEN,
        CHAT_CACHED_INPUT_TOKEN,
        CHAT_OUTPUT_TOKEN,
        EMBEDDING_INPUT_TOKEN,
        SEARCH_BASIC_REQUEST,
        SEARCH_ADVANCED_REQUEST
    }

    record AiPriceQuote(
            long priceVersion,
            UUID priceItemId,
            String providerKey,
            String productKey,
            AiPriceUnit unit,
            long unitSize,
            BigDecimal unitPriceUsd) {
        public AiPriceQuote {
            if (priceVersion < 1 || priceItemId == null || providerKey == null
                    || providerKey.isBlank() || productKey == null || productKey.isBlank()
                    || unit == null || unitSize < 1 || unitPriceUsd == null
                    || unitPriceUsd.signum() < 0 || unitPriceUsd.scale() > 6) {
                throw new IllegalArgumentException("AI price quote is invalid");
            }
        }

        public BigDecimal costFor(long units) {
            if (units < 0) {
                throw new IllegalArgumentException("usage units are invalid");
            }
            return unitPriceUsd
                    .multiply(BigDecimal.valueOf(units))
                    .divide(BigDecimal.valueOf(unitSize), 6, RoundingMode.CEILING);
        }
    }
}
