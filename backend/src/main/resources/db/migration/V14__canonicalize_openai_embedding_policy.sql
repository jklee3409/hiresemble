UPDATE embedding_policy_versions
SET enabled = false
WHERE enabled;

INSERT INTO embedding_policy_versions (
    id,
    version,
    provider_key,
    product_key,
    dimension,
    distance_metric,
    generation,
    enabled,
    created_at
) VALUES (
    '00000000-0000-0000-0000-000000001401',
    2,
    'openai',
    'text-embedding-3-small',
    1536,
    'COSINE',
    1,
    true,
    CURRENT_TIMESTAMP
);
