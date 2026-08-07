CREATE TABLE github_sources (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    source_kind varchar(20) NOT NULL,
    account_type varchar(20) NULL,
    original_url varchar(500) NOT NULL,
    canonical_url varchar(500) NOT NULL,
    owner_login varchar(100) NOT NULL,
    repository_name varchar(100) NULL,
    source_status varchar(30) NOT NULL,
    repository_discovery_truncated boolean NOT NULL DEFAULT false,
    latest_agent_run_id uuid NULL,
    source_revision bigint NOT NULL DEFAULT 0,
    last_successful_sync_at timestamptz NULL,
    new_experience_count integer NOT NULL DEFAULT 0,
    corroborated_experience_count integer NOT NULL DEFAULT 0,
    review_required_count integer NOT NULL DEFAULT 0,
    rejected_candidate_count integer NOT NULL DEFAULT 0,
    snapshot_incomplete boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT github_sources_pk PRIMARY KEY (id),
    CONSTRAINT github_sources_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_sources_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT github_sources_latest_run_owner_fk
        FOREIGN KEY (user_id, latest_agent_run_id) REFERENCES agent_runs(user_id, id),
    CONSTRAINT github_sources_kind_ck CHECK (source_kind IN ('ACCOUNT', 'REPOSITORY')),
    CONSTRAINT github_sources_account_type_ck CHECK (
        account_type IS NULL OR account_type IN ('USER', 'ORGANIZATION')
    ),
    CONSTRAINT github_sources_shape_ck CHECK (
        (source_kind = 'ACCOUNT'
            AND repository_name IS NULL
            AND (account_type IS NOT NULL
                OR source_status IN ('DISCOVERING', 'QUEUED', 'RUNNING', 'FAILED')))
        OR (source_kind = 'REPOSITORY' AND account_type IS NULL AND repository_name IS NOT NULL)
    ),
    CONSTRAINT github_sources_status_ck CHECK (source_status IN (
        'DISCOVERING', 'WAITING_USER', 'QUEUED', 'RUNNING', 'READY', 'PARTIAL', 'FAILED'
    )),
    CONSTRAINT github_sources_url_ck CHECK (
        canonical_url ~ '^https://github[.]com/[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})(?:/[A-Za-z0-9_.-]{1,100})?$'
        AND char_length(original_url) BETWEEN 1 AND 500
    ),
    CONSTRAINT github_sources_owner_ck CHECK (
        owner_login = btrim(owner_login) AND char_length(owner_login) BETWEEN 1 AND 100
    ),
    CONSTRAINT github_sources_repository_ck CHECK (
        repository_name IS NULL OR (
            repository_name = btrim(repository_name)
            AND char_length(repository_name) BETWEEN 1 AND 100
        )
    ),
    CONSTRAINT github_sources_revision_ck CHECK (source_revision >= 0),
    CONSTRAINT github_sources_version_ck CHECK (version >= 0),
    CONSTRAINT github_sources_counts_ck CHECK (
        new_experience_count >= 0
        AND corroborated_experience_count >= 0
        AND review_required_count >= 0
        AND rejected_candidate_count >= 0
    ),
    CONSTRAINT github_sources_time_ck CHECK (
        updated_at >= created_at
        AND (deleted_at IS NULL OR deleted_at >= created_at)
        AND (last_successful_sync_at IS NULL OR last_successful_sync_at >= created_at)
    )
);

CREATE UNIQUE INDEX github_sources_active_canonical_url_uk
    ON github_sources (user_id, canonical_url) WHERE deleted_at IS NULL;
CREATE INDEX github_sources_user_updated_ix
    ON github_sources (user_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX github_sources_worker_ix
    ON github_sources (source_status, updated_at, id) WHERE deleted_at IS NULL;

CREATE TABLE github_repositories (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    external_repository_id bigint NOT NULL,
    node_id varchar(100) NOT NULL,
    owner_login varchar(100) NOT NULL,
    repository_name varchar(100) NOT NULL,
    canonical_url varchar(500) NOT NULL,
    default_branch varchar(255) NOT NULL,
    is_private boolean NOT NULL,
    is_fork boolean NOT NULL,
    is_archived boolean NOT NULL,
    description varchar(500) NULL,
    topics jsonb NOT NULL DEFAULT '[]'::jsonb,
    metadata_etag varchar(255) NULL,
    pushed_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT github_repositories_pk PRIMARY KEY (id),
    CONSTRAINT github_repositories_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_repositories_user_id_external_uk
        UNIQUE (user_id, external_repository_id),
    CONSTRAINT github_repositories_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT github_repositories_public_ck CHECK (NOT is_private),
    CONSTRAINT github_repositories_external_id_ck CHECK (external_repository_id > 0),
    CONSTRAINT github_repositories_identity_ck CHECK (
        char_length(node_id) BETWEEN 1 AND 100
        AND owner_login = btrim(owner_login)
        AND char_length(owner_login) BETWEEN 1 AND 100
        AND repository_name = btrim(repository_name)
        AND char_length(repository_name) BETWEEN 1 AND 100
        AND char_length(default_branch) BETWEEN 1 AND 255
    ),
    CONSTRAINT github_repositories_url_ck CHECK (
        canonical_url ~ '^https://github[.]com/[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9_.-]{1,100}$'
    ),
    CONSTRAINT github_repositories_description_ck CHECK (
        description IS NULL OR char_length(description) <= 500
    ),
    CONSTRAINT github_repositories_topics_ck CHECK (
        jsonb_typeof(topics) = 'array' AND octet_length(topics::text) <= 8192
    ),
    CONSTRAINT github_repositories_etag_ck CHECK (
        metadata_etag IS NULL OR char_length(metadata_etag) BETWEEN 1 AND 255
    ),
    CONSTRAINT github_repositories_time_ck CHECK (updated_at >= created_at)
);

CREATE INDEX github_repositories_user_pushed_ix
    ON github_repositories (user_id, pushed_at DESC NULLS LAST, id);

CREATE TABLE github_source_repository_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    github_source_id uuid NOT NULL,
    github_repository_id uuid NOT NULL,
    available boolean NOT NULL DEFAULT true,
    selected boolean NOT NULL,
    selection_order integer NULL,
    discovered_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT github_source_repository_links_pk PRIMARY KEY (id),
    CONSTRAINT github_source_repository_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_source_repository_links_source_repository_uk
        UNIQUE (user_id, github_source_id, github_repository_id),
    CONSTRAINT github_source_repository_links_source_fk
        FOREIGN KEY (user_id, github_source_id) REFERENCES github_sources(user_id, id),
    CONSTRAINT github_source_repository_links_repository_fk
        FOREIGN KEY (user_id, github_repository_id) REFERENCES github_repositories(user_id, id),
    CONSTRAINT github_source_repository_links_selection_ck CHECK (
        (selected AND selection_order BETWEEN 1 AND 10)
        OR (NOT selected AND selection_order IS NULL)
    ),
    CONSTRAINT github_source_repository_links_time_ck CHECK (updated_at >= discovered_at)
);

CREATE UNIQUE INDEX github_source_repository_links_selected_order_uk
    ON github_source_repository_links (user_id, github_source_id, selection_order)
    WHERE selected;
CREATE INDEX github_source_repository_links_source_query_ix
    ON github_source_repository_links
        (user_id, github_source_id, available, selected, selection_order, updated_at DESC);
CREATE INDEX github_source_repository_links_repository_ix
    ON github_source_repository_links (user_id, github_repository_id, github_source_id);

CREATE TABLE github_repository_snapshots (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    github_repository_id uuid NOT NULL,
    commit_sha char(40) NOT NULL,
    tree_sha char(40) NULL,
    github_api_version varchar(30) NOT NULL,
    retrieval_policy_version varchar(80) NOT NULL,
    selection_complete boolean NOT NULL,
    upstream_truncated boolean NOT NULL,
    snapshot_storage_key varchar(500) NOT NULL,
    checksum_sha256 char(64) NOT NULL,
    sanitized_bytes bigint NOT NULL,
    captured_at timestamptz NOT NULL,
    CONSTRAINT github_repository_snapshots_pk PRIMARY KEY (id),
    CONSTRAINT github_repository_snapshots_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_repository_snapshots_user_repository_id_uk
        UNIQUE (user_id, github_repository_id, id),
    CONSTRAINT github_repository_snapshots_identity_uk
        UNIQUE (user_id, github_repository_id, commit_sha, retrieval_policy_version),
    CONSTRAINT github_repository_snapshots_storage_key_uk UNIQUE (snapshot_storage_key),
    CONSTRAINT github_repository_snapshots_repository_fk
        FOREIGN KEY (user_id, github_repository_id) REFERENCES github_repositories(user_id, id),
    CONSTRAINT github_repository_snapshots_commit_ck CHECK (
        commit_sha ~ '^[0-9a-f]{40}$'
        AND (tree_sha IS NULL OR tree_sha ~ '^[0-9a-f]{40}$')
    ),
    CONSTRAINT github_repository_snapshots_version_ck CHECK (
        char_length(github_api_version) BETWEEN 1 AND 30
        AND char_length(retrieval_policy_version) BETWEEN 1 AND 80
    ),
    CONSTRAINT github_repository_snapshots_truncation_ck CHECK (
        NOT upstream_truncated OR NOT selection_complete
    ),
    CONSTRAINT github_repository_snapshots_storage_ck CHECK (
        snapshot_storage_key ~ '^users/[0-9a-f-]{36}/github-sources/[0-9a-f-]{36}/snapshots/[0-9a-f-]{36}/snapshot[.]json[.]gz$'
        AND checksum_sha256 ~ '^[0-9a-f]{64}$'
        AND sanitized_bytes BETWEEN 1 AND 4000000
    )
);

CREATE INDEX github_repository_snapshots_repository_captured_ix
    ON github_repository_snapshots (user_id, github_repository_id, captured_at DESC);

CREATE TABLE github_source_units (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    snapshot_id uuid NOT NULL,
    unit_type varchar(40) NOT NULL,
    repository_path varchar(1000) NOT NULL,
    blob_sha char(40) NULL,
    language varchar(80) NULL,
    line_start integer NULL,
    line_end integer NULL,
    content_hash char(64) NOT NULL,
    excerpt varchar(500) NOT NULL,
    snapshot_ordinal integer NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT github_source_units_pk PRIMARY KEY (id),
    CONSTRAINT github_source_units_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_source_units_snapshot_ordinal_uk
        UNIQUE (user_id, snapshot_id, snapshot_ordinal),
    CONSTRAINT github_source_units_snapshot_fk
        FOREIGN KEY (user_id, snapshot_id) REFERENCES github_repository_snapshots(user_id, id),
    CONSTRAINT github_source_units_type_ck CHECK (unit_type IN (
        'METADATA', 'README', 'MANIFEST', 'DOCKER', 'CI', 'TEST', 'ARCHITECTURE', 'SOURCE'
    )),
    CONSTRAINT github_source_units_path_ck CHECK (
        repository_path = btrim(repository_path)
        AND char_length(repository_path) BETWEEN 1 AND 1000
        AND repository_path !~ '(^/|\\|[[:cntrl:]])'
        AND repository_path !~ '(^|/)[.][.]($|/)'
    ),
    CONSTRAINT github_source_units_blob_ck CHECK (
        blob_sha IS NULL OR blob_sha ~ '^[0-9a-f]{40}$'
    ),
    CONSTRAINT github_source_units_language_ck CHECK (
        language IS NULL OR char_length(language) BETWEEN 1 AND 80
    ),
    CONSTRAINT github_source_units_line_ck CHECK (
        (line_start IS NULL AND line_end IS NULL)
        OR (line_start >= 1 AND line_end >= line_start)
    ),
    CONSTRAINT github_source_units_content_ck CHECK (
        content_hash ~ '^[0-9a-f]{64}$'
        AND char_length(excerpt) BETWEEN 1 AND 500
        AND snapshot_ordinal >= 1
    )
);

CREATE INDEX github_source_units_snapshot_ix
    ON github_source_units (user_id, snapshot_id, snapshot_ordinal);

ALTER TABLE profile_evidence
    DROP CONSTRAINT profile_evidence_source_type_ck,
    DROP CONSTRAINT profile_evidence_source_shape_ck,
    ADD COLUMN github_source_id uuid NULL,
    ADD COLUMN github_repository_id uuid NULL,
    ADD COLUMN github_snapshot_id uuid NULL,
    ADD COLUMN github_claim_key char(64) NULL,
    ADD CONSTRAINT profile_evidence_github_source_owner_fk
        FOREIGN KEY (user_id, github_source_id) REFERENCES github_sources(user_id, id),
    ADD CONSTRAINT profile_evidence_github_repository_owner_fk
        FOREIGN KEY (user_id, github_repository_id) REFERENCES github_repositories(user_id, id),
    ADD CONSTRAINT profile_evidence_github_snapshot_owner_fk
        FOREIGN KEY (user_id, github_repository_id, github_snapshot_id)
        REFERENCES github_repository_snapshots(user_id, github_repository_id, id),
    ADD CONSTRAINT profile_evidence_github_source_repository_owner_fk
        FOREIGN KEY (user_id, github_source_id, github_repository_id)
        REFERENCES github_source_repository_links(user_id, github_source_id, github_repository_id),
    ADD CONSTRAINT profile_evidence_source_type_ck CHECK (
        source_type IN ('EDUCATION', 'CERTIFICATION', 'LANGUAGE_SCORE', 'AWARD', 'CAREER',
                        'ACTIVITY', 'DOCUMENT_CHUNK', 'EXPERIENCE', 'GITHUB_REPOSITORY', 'MANUAL')
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
        OR (source_type = 'GITHUB_REPOSITORY'
            AND source_entity_id = github_repository_id
            AND document_id IS NULL
            AND github_source_id IS NOT NULL
            AND github_repository_id IS NOT NULL
            AND github_snapshot_id IS NOT NULL
            AND github_claim_key ~ '^[0-9a-f]{64}$')
        OR (source_type = 'MANUAL'
            AND source_entity_id IS NULL
            AND document_id IS NULL)
    ),
    ADD CONSTRAINT profile_evidence_github_columns_ck CHECK (
        source_type = 'GITHUB_REPOSITORY'
        OR num_nonnulls(
            github_source_id, github_repository_id, github_snapshot_id, github_claim_key
        ) = 0
    );

CREATE UNIQUE INDEX profile_evidence_active_github_claim_uk
    ON profile_evidence (user_id, github_repository_id, github_claim_key)
    WHERE source_type = 'GITHUB_REPOSITORY' AND source_deleted_at IS NULL;
CREATE INDEX profile_evidence_github_source_ix
    ON profile_evidence (user_id, github_source_id, updated_at DESC)
    WHERE source_type = 'GITHUB_REPOSITORY';

CREATE TABLE github_evidence_unit_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    profile_evidence_id uuid NOT NULL,
    source_unit_id uuid NOT NULL,
    relation_kind varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT github_evidence_unit_links_pk PRIMARY KEY (id),
    CONSTRAINT github_evidence_unit_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_evidence_unit_links_identity_uk
        UNIQUE (user_id, profile_evidence_id, source_unit_id, relation_kind),
    CONSTRAINT github_evidence_unit_links_evidence_fk
        FOREIGN KEY (user_id, profile_evidence_id) REFERENCES profile_evidence(user_id, id),
    CONSTRAINT github_evidence_unit_links_unit_fk
        FOREIGN KEY (user_id, source_unit_id) REFERENCES github_source_units(user_id, id),
    CONSTRAINT github_evidence_unit_links_relation_ck CHECK (
        relation_kind IN ('PRIMARY', 'SUPPORTING')
    )
);

CREATE UNIQUE INDEX github_evidence_unit_links_primary_uk
    ON github_evidence_unit_links (user_id, profile_evidence_id)
    WHERE relation_kind = 'PRIMARY';
CREATE INDEX github_evidence_unit_links_unit_ix
    ON github_evidence_unit_links (user_id, source_unit_id, created_at DESC);

CREATE TABLE github_snapshot_object_deletion_outbox (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    github_source_id uuid NULL,
    snapshot_id uuid NULL,
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
    CONSTRAINT github_snapshot_object_deletion_outbox_pk PRIMARY KEY (id),
    CONSTRAINT github_snapshot_object_deletion_outbox_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_snapshot_object_deletion_outbox_user_id_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT github_snapshot_object_deletion_outbox_source_fk
        FOREIGN KEY (user_id, github_source_id) REFERENCES github_sources(user_id, id),
    CONSTRAINT github_snapshot_object_deletion_outbox_snapshot_fk
        FOREIGN KEY (user_id, snapshot_id) REFERENCES github_repository_snapshots(user_id, id),
    CONSTRAINT github_snapshot_object_deletion_outbox_reason_ck CHECK (
        reason IN ('SOURCE_DELETE', 'ORPHAN_SNAPSHOT_COMPENSATION')
    ),
    CONSTRAINT github_snapshot_object_deletion_outbox_status_ck CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD')
    ),
    CONSTRAINT github_snapshot_object_deletion_outbox_attempt_ck CHECK (
        attempt_count BETWEEN 0 AND 10
    ),
    CONSTRAINT github_snapshot_object_deletion_outbox_claim_ck CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_token IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT github_snapshot_object_deletion_outbox_terminal_ck CHECK (
        (status IN ('SUCCEEDED', 'DEAD') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'DEAD') AND completed_at IS NULL)
    ),
    CONSTRAINT github_snapshot_object_deletion_outbox_storage_ck CHECK (
        storage_key ~ '^users/[0-9a-f-]{36}/github-sources/[0-9a-f-]{36}/snapshots/[0-9a-f-]{36}/snapshot[.]json[.]gz$'
    )
);

CREATE UNIQUE INDEX github_snapshot_object_deletion_outbox_active_uk
    ON github_snapshot_object_deletion_outbox (storage_key, reason)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX github_snapshot_object_deletion_outbox_due_ix
    ON github_snapshot_object_deletion_outbox (status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX github_snapshot_object_deletion_outbox_source_ix
    ON github_snapshot_object_deletion_outbox (user_id, github_source_id, created_at DESC);

ALTER TABLE agent_runs DROP CONSTRAINT agent_runs_workflow_type_ck;
ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_workflow_type_ck CHECK (workflow_type IN (
        'DOCUMENT_INGESTION', 'JOB_POSTING_EXTRACTION', 'JOB_ANALYSIS',
        'COVER_LETTER_GENERATION', 'COVER_LETTER_VERIFICATION',
        'INTERVIEW_PREPARATION', 'INTERVIEW_ANSWER_FEEDBACK', 'MOCK_INTERVIEW_FEEDBACK',
        'GITHUB_INGESTION'
    ));

ALTER TABLE agent_runs DROP CONSTRAINT agent_runs_waiting_action_ck;
ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_waiting_action_ck CHECK (
        waiting_action_type IS NULL OR waiting_action_type IN (
            'PROVIDE_DOCUMENT_TEXT', 'PROVIDE_JOB_TEXT', 'ENABLE_HIGH_QUALITY',
            'INCREASE_BUDGET', 'SELECT_GITHUB_REPOSITORIES'
        )
    );

ALTER TABLE agent_run_resource_links
    ADD COLUMN github_source_id uuid NULL,
    DROP CONSTRAINT agent_run_resource_links_exactly_one_ck,
    DROP CONSTRAINT agent_run_resource_links_kind_ck,
    DROP CONSTRAINT agent_run_resource_links_secondary_kind_ck,
    ADD CONSTRAINT agent_run_resource_links_github_source_owner_fk
        FOREIGN KEY (user_id, github_source_id) REFERENCES github_sources(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_exactly_one_ck CHECK (
        num_nonnulls(
            document_id,
            job_posting_id,
            job_analysis_id,
            cover_letter_id,
            cover_letter_answer_version_id,
            research_run_id,
            question_set_id,
            interview_answer_version_id,
            github_source_id
        ) = 1
    ),
    ADD CONSTRAINT agent_run_resource_links_kind_ck CHECK (
        (resource_kind = 'DOCUMENT' AND document_id IS NOT NULL)
        OR (resource_kind = 'JOB' AND job_posting_id IS NOT NULL)
        OR (resource_kind = 'JOB_ANALYSIS' AND job_analysis_id IS NOT NULL)
        OR (resource_kind = 'COVER_LETTER' AND cover_letter_id IS NOT NULL)
        OR (resource_kind = 'COVER_LETTER_ANSWER_VERSION'
            AND cover_letter_answer_version_id IS NOT NULL)
        OR (resource_kind = 'RESEARCH_RUN' AND research_run_id IS NOT NULL)
        OR (resource_kind = 'QUESTION_SET' AND question_set_id IS NOT NULL)
        OR (resource_kind = 'INTERVIEW_ANSWER_VERSION'
            AND interview_answer_version_id IS NOT NULL)
        OR (resource_kind = 'GITHUB_SOURCE' AND github_source_id IS NOT NULL)
    ),
    ADD CONSTRAINT agent_run_resource_links_secondary_kind_ck CHECK (
        resource_kind NOT IN (
            'JOB_ANALYSIS', 'COVER_LETTER_ANSWER_VERSION', 'RESEARCH_RUN'
        ) OR NOT primary_resource
    );

CREATE INDEX agent_run_resource_links_github_source_ix
    ON agent_run_resource_links (user_id, github_source_id, created_at DESC)
    WHERE github_source_id IS NOT NULL;

CREATE OR REPLACE FUNCTION assert_agent_run_document_resource_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM agent_run_resource_links link
        JOIN agent_runs run ON run.user_id = link.user_id AND run.id = link.agent_run_id
        WHERE link.primary_resource
          AND (
              (link.resource_kind = 'DOCUMENT'
                  AND (run.resource_type IS DISTINCT FROM 'DOCUMENT'
                      OR run.resource_id IS DISTINCT FROM link.document_id))
              OR (link.resource_kind = 'JOB'
                  AND (run.resource_type IS DISTINCT FROM 'JOB'
                      OR run.resource_id IS DISTINCT FROM link.job_posting_id))
              OR (link.resource_kind = 'COVER_LETTER'
                  AND (run.resource_type IS DISTINCT FROM 'COVER_LETTER'
                      OR run.resource_id IS DISTINCT FROM link.cover_letter_id))
              OR (link.resource_kind = 'QUESTION_SET'
                  AND (run.resource_type IS DISTINCT FROM 'QUESTION_SET'
                      OR run.resource_id IS DISTINCT FROM link.question_set_id))
              OR (link.resource_kind = 'INTERVIEW_ANSWER_VERSION'
                  AND (run.resource_type IS DISTINCT FROM 'INTERVIEW_ANSWER_VERSION'
                      OR run.resource_id IS DISTINCT FROM link.interview_answer_version_id))
              OR (link.resource_kind = 'GITHUB_SOURCE'
                  AND (run.resource_type IS DISTINCT FROM 'GITHUB_SOURCE'
                      OR run.resource_id IS DISTINCT FROM link.github_source_id
                      OR run.workflow_type IS DISTINCT FROM 'GITHUB_INGESTION'))
          )
    ) OR EXISTS (
        SELECT 1
        FROM agent_runs run
        WHERE run.resource_type IN (
            'DOCUMENT', 'JOB', 'COVER_LETTER', 'QUESTION_SET',
            'INTERVIEW_ANSWER_VERSION', 'GITHUB_SOURCE'
        )
          AND NOT EXISTS (
              SELECT 1
              FROM agent_run_resource_links link
              WHERE link.user_id = run.user_id
                AND link.agent_run_id = run.id
                AND link.primary_resource
                AND (
                    (run.resource_type = 'DOCUMENT'
                        AND link.resource_kind = 'DOCUMENT'
                        AND link.document_id = run.resource_id)
                    OR (run.resource_type = 'JOB'
                        AND link.resource_kind = 'JOB'
                        AND link.job_posting_id = run.resource_id)
                    OR (run.resource_type = 'COVER_LETTER'
                        AND link.resource_kind = 'COVER_LETTER'
                        AND link.cover_letter_id = run.resource_id)
                    OR (run.resource_type = 'QUESTION_SET'
                        AND link.resource_kind = 'QUESTION_SET'
                        AND link.question_set_id = run.resource_id)
                    OR (run.resource_type = 'INTERVIEW_ANSWER_VERSION'
                        AND link.resource_kind = 'INTERVIEW_ANSWER_VERSION'
                        AND link.interview_answer_version_id = run.resource_id)
                    OR (run.resource_type = 'GITHUB_SOURCE'
                        AND run.workflow_type = 'GITHUB_INGESTION'
                        AND link.resource_kind = 'GITHUB_SOURCE'
                        AND link.github_source_id = run.resource_id)
                )
          )
    ) OR EXISTS (
        SELECT 1 FROM agent_runs run
        WHERE run.workflow_type = 'GITHUB_INGESTION'
          AND (run.resource_type IS DISTINCT FROM 'GITHUB_SOURCE'
              OR run.resource_id IS NULL)
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'agent_run_typed_resource_parity_ck',
            MESSAGE = 'resource projection must match the authoritative typed link and workflow';
    END IF;
    RETURN NULL;
END;
$$;

CREATE FUNCTION assert_github_source_latest_run_link()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM github_sources source
        WHERE source.latest_agent_run_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM agent_run_resource_links link
              JOIN agent_runs run ON run.user_id = link.user_id AND run.id = link.agent_run_id
              WHERE link.user_id = source.user_id
                AND link.agent_run_id = source.latest_agent_run_id
                AND link.github_source_id = source.id
                AND link.resource_kind = 'GITHUB_SOURCE'
                AND link.primary_resource
                AND run.workflow_type = 'GITHUB_INGESTION'
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'github_sources_latest_run_link_ck',
            MESSAGE = 'latest GitHub source run must own the typed source link';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER github_sources_latest_run_link_ct
AFTER INSERT OR UPDATE OR DELETE ON github_sources
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_source_latest_run_link();

CREATE CONSTRAINT TRIGGER agent_links_latest_github_source_run_ct
AFTER INSERT OR UPDATE OR DELETE ON agent_run_resource_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_source_latest_run_link();

CREATE FUNCTION reject_github_snapshot_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'github_snapshot_immutable_ck',
        MESSAGE = 'GitHub snapshots and source units are immutable';
END;
$$;

CREATE TRIGGER github_repository_snapshots_immutable_tr
BEFORE UPDATE OR DELETE ON github_repository_snapshots
FOR EACH ROW EXECUTE FUNCTION reject_github_snapshot_mutation();

CREATE TRIGGER github_source_units_immutable_tr
BEFORE UPDATE OR DELETE ON github_source_units
FOR EACH ROW EXECUTE FUNCTION reject_github_snapshot_mutation();

CREATE FUNCTION assert_github_evidence_primary_unit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM profile_evidence evidence
        WHERE evidence.source_type = 'GITHUB_REPOSITORY'
          AND NOT EXISTS (
              SELECT 1
              FROM github_evidence_unit_links link
              JOIN github_source_units unit
                ON unit.user_id = link.user_id AND unit.id = link.source_unit_id
              JOIN github_repository_snapshots snapshot
                ON snapshot.user_id = unit.user_id AND snapshot.id = unit.snapshot_id
              WHERE link.user_id = evidence.user_id
                AND link.profile_evidence_id = evidence.id
                AND link.relation_kind = 'PRIMARY'
                AND snapshot.id = evidence.github_snapshot_id
                AND snapshot.github_repository_id = evidence.github_repository_id
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'github_evidence_primary_unit_ck',
            MESSAGE = 'GitHub evidence must have one owner-matched primary source unit';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER profile_evidence_github_primary_unit_ct
AFTER INSERT OR UPDATE OR DELETE ON profile_evidence
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_evidence_primary_unit();

CREATE CONSTRAINT TRIGGER github_evidence_links_primary_unit_ct
AFTER INSERT OR UPDATE OR DELETE ON github_evidence_unit_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_evidence_primary_unit();
