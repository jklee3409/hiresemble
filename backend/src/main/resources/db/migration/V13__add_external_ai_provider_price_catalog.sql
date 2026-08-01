ALTER TABLE ai_usage_records
    ADD COLUMN provider_call_id uuid NULL;

CREATE UNIQUE INDEX ai_usage_records_provider_call_price_item_uk
    ON ai_usage_records (
        user_id, agent_run_id, agent_step_id, provider_call_id, price_item_id
    )
    WHERE provider_call_id IS NOT NULL;

INSERT INTO ai_price_versions (
    id, version, catalog_key, effective_from, effective_to, created_at
) VALUES (
    '85000000-0000-4000-8000-000000000001',
    2026073101,
    'external-provider-public-payg-2026-07-31',
    TIMESTAMPTZ '2026-07-31 00:00:00+09',
    NULL,
    CURRENT_TIMESTAMP
);

INSERT INTO ai_price_items (
    id, price_version, provider_key, product_key, unit,
    unit_size, unit_price_usd, created_at
) VALUES
    ('85000000-0000-4000-8000-000000000101', 2026073101, 'openai', 'gpt-5-mini',
     'CHAT_INPUT_TOKEN', 1000000, 0.250000, CURRENT_TIMESTAMP),
    ('85000000-0000-4000-8000-000000000102', 2026073101, 'openai', 'gpt-5-mini',
     'CHAT_CACHED_INPUT_TOKEN', 1000000, 0.025000, CURRENT_TIMESTAMP),
    ('85000000-0000-4000-8000-000000000103', 2026073101, 'openai', 'gpt-5-mini',
     'CHAT_OUTPUT_TOKEN', 1000000, 2.000000, CURRENT_TIMESTAMP),
    ('85000000-0000-4000-8000-000000000104', 2026073101, 'openai',
     'text-embedding-3-small', 'EMBEDDING_INPUT_TOKEN',
     1000000, 0.020000, CURRENT_TIMESTAMP),
    ('85000000-0000-4000-8000-000000000105', 2026073101, 'tavily', 'basic',
     'SEARCH_BASIC_REQUEST', 1, 0.008000, CURRENT_TIMESTAMP),
    ('85000000-0000-4000-8000-000000000106', 2026073101, 'tavily', 'advanced',
     'SEARCH_ADVANCED_REQUEST', 1, 0.016000, CURRENT_TIMESTAMP);
