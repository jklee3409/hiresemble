ALTER TABLE profile_evidence DROP CONSTRAINT profile_evidence_source_type_ck;
ALTER TABLE profile_evidence DROP CONSTRAINT profile_evidence_source_shape_ck;

ALTER TABLE profile_evidence
    ADD CONSTRAINT profile_evidence_source_type_ck CHECK (
        source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER',
                        'ACTIVITY', 'DOCUMENT_CHUNK', 'EXPERIENCE', 'MANUAL')
    ),
    ADD CONSTRAINT profile_evidence_source_shape_ck CHECK (
        (source_type IN ('CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER', 'ACTIVITY')
            AND source_entity_id IS NOT NULL
            AND document_id IS NULL
            AND verification_status <> 'SOURCE_DELETED')
        OR (source_type = 'EDUCATION'
            AND source_entity_id IS NULL
            AND document_id IS NULL
            AND verification_status = 'SOURCE_DELETED')
        OR (source_type = 'DOCUMENT_CHUNK'
            AND verification_status <> 'SOURCE_DELETED'
            AND source_entity_id IS NOT NULL
            AND document_id IS NOT NULL)
        OR (source_type = 'DOCUMENT_CHUNK'
            AND verification_status = 'SOURCE_DELETED'
            AND source_entity_id IS NULL
            AND document_id IS NULL)
        OR (source_type = 'EXPERIENCE'
            AND verification_status <> 'SOURCE_DELETED'
            AND source_entity_id IS NOT NULL
            AND document_id IS NULL)
        OR (source_type = 'MANUAL'
            AND source_entity_id IS NULL
            AND document_id IS NULL)
    );

-- profile_evidence has an INITIALLY DEFERRED constraint trigger. Create this index before
-- the backfill INSERT queues trigger events on populated upgrade databases.
CREATE UNIQUE INDEX profile_evidence_one_experience_source_ix
    ON profile_evidence (user_id, source_type, source_entity_id)
    WHERE source_type = 'EXPERIENCE';

CREATE TABLE experience_items (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    canonical_evidence_id uuid NULL,
    evidence_category varchar(80) NOT NULL,
    title varchar(250) NOT NULL,
    content varchar(20000) NOT NULL,
    verification_status varchar(30) NOT NULL,
    match_kind varchar(30) NOT NULL,
    matched_experience_item_id uuid NULL,
    match_similarity numeric(6,5) NULL,
    match_policy_version varchar(50) NOT NULL,
    canonical_fingerprint varchar(64) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT experience_items_pk PRIMARY KEY (id),
    CONSTRAINT experience_items_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT experience_items_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT experience_items_match_owner_fk
        FOREIGN KEY (user_id, matched_experience_item_id)
        REFERENCES experience_items(user_id, id),
    CONSTRAINT experience_items_category_ck CHECK (
        evidence_category = btrim(evidence_category)
        AND char_length(evidence_category) BETWEEN 1 AND 80
    ),
    CONSTRAINT experience_items_title_ck CHECK (
        title = btrim(title)
        AND char_length(title) BETWEEN 1 AND 250
        AND title !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT experience_items_content_ck CHECK (char_length(content) BETWEEN 1 AND 20000),
    CONSTRAINT experience_items_verification_status_ck CHECK (
        verification_status IN ('PENDING', 'VERIFIED', 'REJECTED')
    ),
    CONSTRAINT experience_items_match_kind_ck CHECK (
        match_kind IN ('NEW', 'RELATED_DIFFERENT', 'CONFLICT')
    ),
    CONSTRAINT experience_items_match_shape_ck CHECK (
        (match_kind = 'NEW' AND matched_experience_item_id IS NULL AND match_similarity IS NULL)
        OR (match_kind IN ('RELATED_DIFFERENT', 'CONFLICT')
            AND matched_experience_item_id IS NOT NULL
            AND match_similarity BETWEEN 0 AND 1)
    ),
    CONSTRAINT experience_items_fingerprint_ck CHECK (
        canonical_fingerprint ~ '^[0-9a-f]{32,64}$'
    ),
    CONSTRAINT experience_items_version_ck CHECK (version >= 0),
    CONSTRAINT experience_items_time_ck CHECK (
        updated_at >= created_at AND (deleted_at IS NULL OR deleted_at >= created_at)
    )
);

CREATE UNIQUE INDEX experience_items_active_fingerprint_ix
    ON experience_items (user_id, evidence_category, canonical_fingerprint)
    WHERE deleted_at IS NULL;
CREATE INDEX experience_items_user_updated_ix
    ON experience_items (user_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX experience_items_match_review_ix
    ON experience_items (user_id, match_kind, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL AND match_kind <> 'NEW';

CREATE TABLE experience_evidence_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    experience_item_id uuid NOT NULL,
    profile_evidence_id uuid NOT NULL,
    relation_kind varchar(30) NOT NULL,
    similarity numeric(6,5) NULL,
    match_policy_version varchar(50) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT experience_evidence_links_pk PRIMARY KEY (id),
    CONSTRAINT experience_evidence_links_source_uk UNIQUE (user_id, profile_evidence_id),
    CONSTRAINT experience_evidence_links_item_owner_fk
        FOREIGN KEY (user_id, experience_item_id)
        REFERENCES experience_items(user_id, id) ON DELETE CASCADE,
    CONSTRAINT experience_evidence_links_evidence_owner_fk
        FOREIGN KEY (user_id, profile_evidence_id)
        REFERENCES profile_evidence(user_id, id) ON DELETE CASCADE,
    CONSTRAINT experience_evidence_links_relation_ck CHECK (
        relation_kind IN ('PRIMARY_SOURCE', 'CORROBORATING')
    ),
    CONSTRAINT experience_evidence_links_similarity_ck CHECK (
        similarity IS NULL OR similarity BETWEEN 0 AND 1
    )
);

CREATE INDEX experience_evidence_links_item_ix
    ON experience_evidence_links (user_id, experience_item_id, created_at, id);

CREATE TABLE experience_item_embeddings (
    user_id uuid NOT NULL,
    experience_item_id uuid NOT NULL,
    experience_version bigint NOT NULL,
    embedding vector(1536) NOT NULL,
    embedding_policy_version bigint NOT NULL,
    embedding_provider varchar(50) NOT NULL,
    embedding_model varchar(150) NOT NULL,
    embedding_dimension integer NOT NULL,
    embedding_generation integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT experience_item_embeddings_pk
        PRIMARY KEY (user_id, experience_item_id, experience_version,
                     embedding_policy_version, embedding_generation),
    CONSTRAINT experience_item_embeddings_item_owner_fk
        FOREIGN KEY (user_id, experience_item_id)
        REFERENCES experience_items(user_id, id) ON DELETE CASCADE,
    CONSTRAINT experience_item_embeddings_policy_fk
        FOREIGN KEY (embedding_policy_version)
        REFERENCES embedding_policy_versions(version),
    CONSTRAINT experience_item_embeddings_shape_ck CHECK (
        experience_version >= 0
        AND embedding_dimension = 1536
        AND embedding_generation >= 1
    )
);

CREATE INDEX experience_item_embeddings_exact_cosine_ix
    ON experience_item_embeddings (
        user_id, embedding_policy_version, embedding_generation, experience_item_id
    );

CREATE TEMP TABLE experience_backfill_map ON COMMIT DROP AS
SELECT DISTINCT ON (
        evidence.user_id,
        evidence.evidence_category,
        md5(lower(regexp_replace(
            evidence.evidence_category || '|' || evidence.title || '|' || evidence.content,
            '[[:space:][:punct:]]+', '', 'g')))
    )
    evidence.user_id,
    evidence.evidence_category,
    md5(lower(regexp_replace(
        evidence.evidence_category || '|' || evidence.title || '|' || evidence.content,
        '[[:space:][:punct:]]+', '', 'g'))) AS canonical_fingerprint,
    evidence.id AS representative_evidence_id,
    gen_random_uuid() AS experience_item_id,
    gen_random_uuid() AS canonical_evidence_id
FROM profile_evidence evidence
WHERE evidence.source_type = 'DOCUMENT_CHUNK'
  AND evidence.verification_status <> 'SOURCE_DELETED'
  AND evidence.source_deleted_at IS NULL
ORDER BY evidence.user_id, evidence.evidence_category, canonical_fingerprint,
         (evidence.verification_status = 'VERIFIED') DESC,
         evidence.updated_at DESC, evidence.id;

INSERT INTO experience_items (
    id, user_id, canonical_evidence_id, evidence_category, title, content,
    verification_status, match_kind, matched_experience_item_id, match_similarity,
    match_policy_version, canonical_fingerprint, version, created_at, updated_at, deleted_at
)
SELECT mapping.experience_item_id, source.user_id, NULL, source.evidence_category,
       source.title, source.content, source.verification_status, 'NEW', NULL, NULL,
       'experience-semantic-v1', mapping.canonical_fingerprint, 0,
       source.created_at, source.updated_at, NULL
FROM experience_backfill_map mapping
JOIN profile_evidence source ON source.id = mapping.representative_evidence_id;

INSERT INTO profile_evidence (
    id, user_id, source_type, source_entity_id, document_id, evidence_category,
    title, content, metadata, confidence, verification_status, verified_at,
    source_deleted_at, version, created_at, updated_at
)
SELECT mapping.canonical_evidence_id, source.user_id, 'EXPERIENCE',
       mapping.experience_item_id, NULL, source.evidence_category, source.title,
       source.content, source.metadata, source.confidence, source.verification_status,
       source.verified_at, NULL, 0, source.created_at, source.updated_at
FROM experience_backfill_map mapping
JOIN profile_evidence source ON source.id = mapping.representative_evidence_id;

UPDATE experience_items item
SET canonical_evidence_id = mapping.canonical_evidence_id
FROM experience_backfill_map mapping
WHERE item.id = mapping.experience_item_id;

INSERT INTO experience_evidence_links (
    id, user_id, experience_item_id, profile_evidence_id, relation_kind,
    similarity, match_policy_version, created_at
)
SELECT gen_random_uuid(), source.user_id, mapping.experience_item_id, source.id,
       CASE WHEN source.id = mapping.representative_evidence_id
            THEN 'PRIMARY_SOURCE' ELSE 'CORROBORATING' END,
       CASE WHEN source.id = mapping.representative_evidence_id THEN NULL ELSE 1.00000 END,
       'experience-semantic-v1', source.created_at
FROM profile_evidence source
JOIN experience_backfill_map mapping
  ON mapping.user_id = source.user_id
 AND mapping.evidence_category = source.evidence_category
 AND mapping.canonical_fingerprint = md5(lower(regexp_replace(
        source.evidence_category || '|' || source.title || '|' || source.content,
        '[[:space:][:punct:]]+', '', 'g')))
WHERE source.source_type = 'DOCUMENT_CHUNK'
  AND source.verification_status <> 'SOURCE_DELETED'
  AND source.source_deleted_at IS NULL;

INSERT INTO experience_item_embeddings (
    user_id, experience_item_id, experience_version, embedding,
    embedding_policy_version, embedding_provider, embedding_model,
    embedding_dimension, embedding_generation, created_at
)
SELECT source.user_id, mapping.experience_item_id, 0, chunk.embedding,
       chunk.embedding_policy_version, chunk.embedding_provider, chunk.embedding_model,
       chunk.embedding_dimension, chunk.embedding_generation, source.updated_at
FROM experience_backfill_map mapping
JOIN profile_evidence source ON source.id = mapping.representative_evidence_id
JOIN document_chunks chunk
  ON chunk.user_id = source.user_id
 AND chunk.document_id = source.document_id
 AND chunk.id = source.source_entity_id
WHERE chunk.embedding IS NOT NULL;

ALTER TABLE experience_items
    ALTER COLUMN canonical_evidence_id SET NOT NULL,
    ADD CONSTRAINT experience_items_canonical_evidence_owner_fk
        FOREIGN KEY (user_id, canonical_evidence_id)
        REFERENCES profile_evidence(user_id, id)
        DEFERRABLE INITIALLY DEFERRED;
