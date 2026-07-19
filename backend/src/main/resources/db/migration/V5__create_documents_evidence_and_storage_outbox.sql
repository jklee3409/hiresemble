CREATE TABLE documents (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    document_type varchar(40) NOT NULL,
    original_filename varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    storage_key varchar(500) NOT NULL,
    mime_type varchar(100) NOT NULL,
    file_size_bytes bigint NOT NULL,
    checksum_sha256 char(64) NOT NULL,
    parse_status varchar(30) NOT NULL,
    evidence_extraction_status varchar(30) NOT NULL,
    parse_error_code varchar(100) NULL,
    evidence_error_code varchar(100) NULL,
    manual_text_provided boolean NOT NULL DEFAULT false,
    source_revision bigint NOT NULL DEFAULT 1,
    latest_agent_run_id uuid NULL,
    version bigint NOT NULL DEFAULT 0,
    uploaded_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT documents_pk PRIMARY KEY (id),
    CONSTRAINT documents_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT documents_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT documents_storage_key_uk UNIQUE (storage_key),
    CONSTRAINT documents_type_ck CHECK (document_type IN (
        'RESUME', 'PORTFOLIO', 'CAREER_DESCRIPTION', 'CERTIFICATE',
        'TRANSCRIPT', 'OTHER'
    )),
    CONSTRAINT documents_filename_ck CHECK (
        original_filename = btrim(original_filename)
        AND char_length(original_filename) BETWEEN 1 AND 255
        AND original_filename !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT documents_display_name_ck CHECK (
        display_name = btrim(display_name)
        AND char_length(display_name) BETWEEN 1 AND 255
        AND display_name !~ '[[:cntrl:]/\\]'
    ),
    CONSTRAINT documents_storage_key_ck CHECK (
        storage_key = 'users/' || user_id::text || '/documents/' || id::text || '/content'
    ),
    CONSTRAINT documents_mime_type_ck CHECK (
        mime_type IN (
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'text/plain'
        )
    ),
    CONSTRAINT documents_file_size_ck CHECK (file_size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT documents_checksum_ck CHECK (checksum_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT documents_parse_status_ck CHECK (
        parse_status IN ('UPLOADED', 'PARSING', 'PARSED', 'NEEDS_MANUAL_TEXT', 'FAILED')
    ),
    CONSTRAINT documents_evidence_status_ck CHECK (
        evidence_extraction_status IN ('NOT_STARTED', 'QUEUED', 'EXTRACTING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT documents_parse_error_ck CHECK (
        (parse_status = 'FAILED' AND parse_error_code IS NOT NULL)
        OR (parse_status <> 'FAILED' AND parse_error_code IS NULL)
    ),
    CONSTRAINT documents_evidence_error_ck CHECK (
        (evidence_extraction_status = 'FAILED' AND evidence_error_code IS NOT NULL)
        OR (evidence_extraction_status <> 'FAILED' AND evidence_error_code IS NULL)
    ),
    CONSTRAINT documents_source_revision_ck CHECK (source_revision >= 1),
    CONSTRAINT documents_version_ck CHECK (version >= 0),
    CONSTRAINT documents_time_ck CHECK (
        updated_at >= uploaded_at AND (deleted_at IS NULL OR deleted_at >= uploaded_at)
    )
);

CREATE INDEX documents_owner_uploaded_ix
    ON documents (user_id, uploaded_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX documents_owner_updated_ix
    ON documents (user_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX documents_owner_checksum_ix
    ON documents (user_id, checksum_sha256) WHERE deleted_at IS NULL;

CREATE TABLE document_texts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    document_id uuid NOT NULL,
    source_revision bigint NOT NULL,
    extracted_text text NULL,
    masked_text text NULL,
    character_count integer NOT NULL,
    page_count integer NULL,
    page_offsets jsonb NOT NULL DEFAULT '[]'::jsonb,
    parser_name varchar(150) NULL,
    parser_version varchar(150) NULL,
    parsed_at timestamptz NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT document_texts_pk PRIMARY KEY (id),
    CONSTRAINT document_texts_owner_revision_uk UNIQUE (user_id, document_id, source_revision),
    CONSTRAINT document_texts_document_owner_fk FOREIGN KEY (user_id, document_id)
        REFERENCES documents(user_id, id) ON DELETE CASCADE,
    CONSTRAINT document_texts_revision_ck CHECK (source_revision >= 1),
    CONSTRAINT document_texts_extracted_text_ck CHECK (
        extracted_text IS NULL OR char_length(extracted_text) <= 500000
    ),
    CONSTRAINT document_texts_masked_text_ck CHECK (
        masked_text IS NULL OR char_length(masked_text) <= 500000
    ),
    CONSTRAINT document_texts_character_count_ck CHECK (character_count BETWEEN 0 AND 500000),
    CONSTRAINT document_texts_page_count_ck CHECK (page_count IS NULL OR page_count >= 1),
    CONSTRAINT document_texts_page_offsets_ck CHECK (jsonb_typeof(page_offsets) = 'array'),
    CONSTRAINT document_texts_parser_pair_ck CHECK (
        (parser_name IS NULL AND parser_version IS NULL)
        OR (parser_name IS NOT NULL AND parser_version IS NOT NULL)
    ),
    CONSTRAINT document_texts_version_ck CHECK (version >= 0),
    CONSTRAINT document_texts_time_ck CHECK (
        updated_at >= created_at AND (parsed_at IS NULL OR parsed_at >= created_at)
    )
);

CREATE TABLE document_chunks (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    document_id uuid NOT NULL,
    source_revision bigint NOT NULL,
    chunk_index integer NOT NULL,
    page_from integer NULL,
    page_to integer NULL,
    content text NOT NULL,
    masked_content text NOT NULL,
    token_count integer NOT NULL,
    embedding vector(1536) NULL,
    embedding_policy_version bigint NULL,
    embedding_provider varchar(50) NULL,
    embedding_model varchar(150) NULL,
    embedding_dimension integer NULL,
    embedding_generation integer NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL,
    CONSTRAINT document_chunks_pk PRIMARY KEY (id),
    CONSTRAINT document_chunks_owner_id_uk UNIQUE (user_id, document_id, id),
    CONSTRAINT document_chunks_owner_revision_index_uk
        UNIQUE (user_id, document_id, source_revision, chunk_index),
    CONSTRAINT document_chunks_document_owner_fk FOREIGN KEY (user_id, document_id)
        REFERENCES documents(user_id, id) ON DELETE CASCADE,
    CONSTRAINT document_chunks_embedding_policy_fk FOREIGN KEY (embedding_policy_version)
        REFERENCES embedding_policy_versions(version),
    CONSTRAINT document_chunks_revision_ck CHECK (source_revision >= 1),
    CONSTRAINT document_chunks_index_ck CHECK (chunk_index >= 0),
    CONSTRAINT document_chunks_page_pair_ck CHECK (
        (page_from IS NULL AND page_to IS NULL)
        OR (page_from IS NOT NULL AND page_to IS NOT NULL
            AND page_from >= 1 AND page_to >= page_from)
    ),
    CONSTRAINT document_chunks_content_ck CHECK (
        char_length(content) BETWEEN 1 AND 500000
        AND char_length(masked_content) BETWEEN 1 AND 500000
    ),
    CONSTRAINT document_chunks_token_count_ck CHECK (token_count >= 0),
    CONSTRAINT document_chunks_embedding_shape_ck CHECK (
        (embedding IS NULL
            AND embedding_policy_version IS NULL
            AND embedding_provider IS NULL
            AND embedding_model IS NULL
            AND embedding_dimension IS NULL
            AND embedding_generation IS NULL)
        OR (embedding IS NOT NULL
            AND embedding_policy_version IS NOT NULL
            AND embedding_provider IS NOT NULL
            AND embedding_model IS NOT NULL
            AND embedding_dimension = 1536
            AND embedding_generation IS NOT NULL)
    ),
    CONSTRAINT document_chunks_metadata_ck CHECK (jsonb_typeof(metadata) = 'object')
);

CREATE INDEX document_chunks_exact_cosine_ix
    ON document_chunks (user_id, embedding_policy_version, embedding_generation, document_id)
    WHERE embedding IS NOT NULL;

CREATE TABLE object_deletion_outbox (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    document_id uuid NULL,
    storage_key varchar(500) NOT NULL,
    reason varchar(50) NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    claim_token uuid NULL,
    lease_expires_at timestamptz NULL,
    last_error_code varchar(100) NULL,
    created_at timestamptz NOT NULL,
    completed_at timestamptz NULL,
    CONSTRAINT object_deletion_outbox_pk PRIMARY KEY (id),
    CONSTRAINT object_deletion_outbox_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT object_deletion_outbox_document_owner_fk FOREIGN KEY (user_id, document_id)
        REFERENCES documents(user_id, id),
    CONSTRAINT object_deletion_outbox_reason_ck CHECK (
        (reason = 'DOCUMENT_DELETE' AND document_id IS NOT NULL)
        OR (reason = 'ORPHAN_UPLOAD_COMPENSATION' AND document_id IS NULL)
    ),
    CONSTRAINT object_deletion_outbox_status_ck CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD')
    ),
    CONSTRAINT object_deletion_outbox_attempt_ck CHECK (attempt_count BETWEEN 0 AND 10),
    CONSTRAINT object_deletion_outbox_claim_ck CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_token IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT object_deletion_outbox_terminal_ck CHECK (
        (status IN ('SUCCEEDED', 'DEAD') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'DEAD') AND completed_at IS NULL)
    )
);

CREATE UNIQUE INDEX object_deletion_outbox_active_uk
    ON object_deletion_outbox (
        COALESCE(document_id, '00000000-0000-0000-0000-000000000000'::uuid),
        storage_key,
        reason
    ) WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX object_deletion_outbox_due_ix
    ON object_deletion_outbox (status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE agent_run_resource_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    agent_run_id uuid NOT NULL,
    resource_kind varchar(50) NOT NULL,
    document_id uuid NOT NULL,
    primary_resource boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    CONSTRAINT agent_run_resource_links_pk PRIMARY KEY (id),
    CONSTRAINT agent_run_resource_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT agent_run_resource_links_run_owner_fk FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id) ON DELETE CASCADE,
    CONSTRAINT agent_run_resource_links_document_owner_fk FOREIGN KEY (user_id, document_id)
        REFERENCES documents(user_id, id),
    CONSTRAINT agent_run_resource_links_kind_ck CHECK (resource_kind = 'DOCUMENT'),
    CONSTRAINT agent_run_resource_links_primary_ck CHECK (primary_resource)
);

CREATE UNIQUE INDEX agent_run_resource_links_primary_run_uk
    ON agent_run_resource_links (user_id, agent_run_id) WHERE primary_resource;
CREATE INDEX agent_run_resource_links_document_ix
    ON agent_run_resource_links (user_id, document_id, created_at DESC);

ALTER TABLE documents
    ADD CONSTRAINT documents_latest_run_owner_fk FOREIGN KEY (user_id, latest_agent_run_id)
        REFERENCES agent_runs(user_id, id);

-- P2 exposed future document UUID slots before an owner aggregate existed. They cannot
-- satisfy the new owner FK, so the forward migration removes those non-authoritative hints.
UPDATE certifications SET evidence_document_id = NULL WHERE evidence_document_id IS NOT NULL;
UPDATE language_scores SET evidence_document_id = NULL WHERE evidence_document_id IS NOT NULL;
UPDATE awards SET evidence_document_id = NULL WHERE evidence_document_id IS NOT NULL;
SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

ALTER TABLE certifications
    ADD CONSTRAINT certifications_document_owner_fk FOREIGN KEY (user_id, evidence_document_id)
        REFERENCES documents(user_id, id);
ALTER TABLE language_scores
    ADD CONSTRAINT language_scores_document_owner_fk FOREIGN KEY (user_id, evidence_document_id)
        REFERENCES documents(user_id, id);
ALTER TABLE awards
    ADD CONSTRAINT awards_document_owner_fk FOREIGN KEY (user_id, evidence_document_id)
        REFERENCES documents(user_id, id);

-- V3 allowed prospective document evidence without a document/chunk FK. Preserve its
-- evidence identity as a privacy-safe tombstone rather than inventing a source aggregate.
ALTER TABLE profile_evidence DROP CONSTRAINT profile_evidence_source_shape_ck;

UPDATE profile_evidence
SET source_entity_id = NULL,
    document_id = NULL,
    title = '[SOURCE DELETED]',
    content = '[SOURCE DELETED]',
    metadata = '{}'::jsonb,
    confidence = NULL,
    verification_status = 'SOURCE_DELETED',
    verified_at = NULL,
    source_deleted_at = COALESCE(source_deleted_at, CURRENT_TIMESTAMP),
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE source_type = 'DOCUMENT_CHUNK';
SET CONSTRAINTS ALL IMMEDIATE;
SET CONSTRAINTS ALL DEFERRED;

ALTER TABLE profile_evidence
    ADD CONSTRAINT profile_evidence_source_shape_ck CHECK (
        (source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER')
            AND source_entity_id IS NOT NULL AND document_id IS NULL
            AND verification_status <> 'SOURCE_DELETED')
        OR (source_type = 'DOCUMENT_CHUNK'
            AND verification_status <> 'SOURCE_DELETED'
            AND source_entity_id IS NOT NULL AND document_id IS NOT NULL)
        OR (source_type = 'DOCUMENT_CHUNK'
            AND verification_status = 'SOURCE_DELETED'
            AND source_entity_id IS NULL AND document_id IS NULL)
        OR (source_type = 'MANUAL' AND source_entity_id IS NULL AND document_id IS NULL)
    ),
    ADD CONSTRAINT profile_evidence_document_owner_fk FOREIGN KEY (user_id, document_id)
        REFERENCES documents(user_id, id),
    ADD CONSTRAINT profile_evidence_chunk_owner_fk FOREIGN KEY (user_id, document_id, source_entity_id)
        REFERENCES document_chunks(user_id, document_id, id);

-- P3 could carry a generic DOCUMENT projection even though no Document aggregate existed.
-- A typed link is authoritative from V5 onward, so orphan projections are retired.
UPDATE agent_runs
SET resource_type = NULL, resource_id = NULL
WHERE resource_type = 'DOCUMENT';

CREATE FUNCTION assert_agent_run_document_resource_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM agent_run_resource_links link
        JOIN agent_runs run ON run.user_id = link.user_id AND run.id = link.agent_run_id
        WHERE link.resource_kind = 'DOCUMENT'
          AND (run.resource_type IS DISTINCT FROM 'DOCUMENT'
               OR run.resource_id IS DISTINCT FROM link.document_id)
    ) OR EXISTS (
        SELECT 1
        FROM agent_runs run
        WHERE run.resource_type = 'DOCUMENT'
          AND NOT EXISTS (
              SELECT 1 FROM agent_run_resource_links link
              WHERE link.user_id = run.user_id
                AND link.agent_run_id = run.id
                AND link.resource_kind = 'DOCUMENT'
                AND link.document_id = run.resource_id
                AND link.primary_resource
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'agent_run_document_resource_parity_ck',
            MESSAGE = 'document resource projection must match the authoritative typed link';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER agent_runs_document_resource_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON agent_runs
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_agent_run_document_resource_parity();

CREATE CONSTRAINT TRIGGER agent_run_links_document_resource_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON agent_run_resource_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_agent_run_document_resource_parity();

CREATE FUNCTION assert_document_latest_run_link()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM documents document
        WHERE document.latest_agent_run_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM agent_run_resource_links link
              WHERE link.user_id = document.user_id
                AND link.agent_run_id = document.latest_agent_run_id
                AND link.document_id = document.id
                AND link.resource_kind = 'DOCUMENT'
                AND link.primary_resource
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'documents_latest_run_link_ck',
            MESSAGE = 'latest document run must own the typed document resource link';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER documents_latest_run_link_ct
AFTER INSERT OR UPDATE OR DELETE ON documents
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_document_latest_run_link();

CREATE CONSTRAINT TRIGGER agent_links_latest_document_run_ct
AFTER INSERT OR UPDATE OR DELETE ON agent_run_resource_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_document_latest_run_link();

-- Agent Step checkpoints require an immutable model-policy FK even when the
-- production provider is disabled. This policy contains no price or credential
-- data and does not enable a network adapter.
INSERT INTO ai_model_policies (
    id, version, policy_json, active, created_at
) VALUES (
    '00000000-0000-0000-0000-000000000402', 1,
    '{"providerEnabled":false,"providerKey":"none","lowCostProductKey":"none","balancedProductKey":"none","highQualityProductKey":"none","highQualityWorkflowAllowlist":[]}'::jsonb,
    true, CURRENT_TIMESTAMP
);

INSERT INTO embedding_policy_versions (
    id, version, provider_key, product_key, dimension,
    distance_metric, generation, enabled, created_at
) VALUES (
    '00000000-0000-0000-0000-000000000401', 1,
    'OpenAI', 'text-embedding-3-small', 1536,
    'COSINE', 1, true, CURRENT_TIMESTAMP
);
