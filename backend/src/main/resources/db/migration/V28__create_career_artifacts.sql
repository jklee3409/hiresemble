CREATE TABLE career_artifacts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    artifact_type varchar(20) NOT NULL,
    title varchar(120) NOT NULL,
    lifecycle_status varchar(20) NOT NULL,
    current_version_id uuid NULL,
    latest_agent_run_id uuid NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT career_artifacts_pk PRIMARY KEY (id),
    CONSTRAINT career_artifacts_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT career_artifacts_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT career_artifacts_latest_run_owner_fk
        FOREIGN KEY (user_id, latest_agent_run_id) REFERENCES agent_runs(user_id, id),
    CONSTRAINT career_artifacts_type_ck CHECK (artifact_type IN ('RESUME', 'PORTFOLIO')),
    CONSTRAINT career_artifacts_lifecycle_ck CHECK (
        lifecycle_status IN ('ACTIVE', 'ARCHIVED')
    ),
    CONSTRAINT career_artifacts_title_ck CHECK (
        title = btrim(title)
        AND char_length(title) BETWEEN 1 AND 120
        AND title !~ '[[:cntrl:]]'
    ),
    CONSTRAINT career_artifacts_version_ck CHECK (version >= 0),
    CONSTRAINT career_artifacts_time_ck CHECK (
        updated_at >= created_at AND (deleted_at IS NULL OR deleted_at >= created_at)
    )
);

CREATE INDEX career_artifacts_owner_updated_ix
    ON career_artifacts (user_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX career_artifacts_owner_type_updated_ix
    ON career_artifacts (user_id, artifact_type, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX career_artifacts_owner_lifecycle_updated_ix
    ON career_artifacts (user_id, lifecycle_status, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE career_artifact_versions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    career_artifact_id uuid NOT NULL,
    version_no integer NOT NULL,
    content_schema_version varchar(80) NOT NULL,
    content_json jsonb NOT NULL,
    template_key varchar(80) NOT NULL,
    template_version varchar(40) NOT NULL,
    model_id varchar(64) NOT NULL,
    agent_run_id uuid NOT NULL,
    render_profile_snapshot jsonb NOT NULL,
    storage_key varchar(500) NOT NULL,
    mime_type varchar(100) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 char(64) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT career_artifact_versions_pk PRIMARY KEY (id),
    CONSTRAINT career_artifact_versions_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT career_artifact_versions_owner_artifact_id_uk
        UNIQUE (user_id, career_artifact_id, id),
    CONSTRAINT career_artifact_versions_number_uk
        UNIQUE (user_id, career_artifact_id, version_no),
    CONSTRAINT career_artifact_versions_run_uk UNIQUE (user_id, agent_run_id),
    CONSTRAINT career_artifact_versions_storage_key_uk UNIQUE (storage_key),
    CONSTRAINT career_artifact_versions_artifact_owner_fk
        FOREIGN KEY (user_id, career_artifact_id)
        REFERENCES career_artifacts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT career_artifact_versions_run_owner_fk
        FOREIGN KEY (user_id, agent_run_id) REFERENCES agent_runs(user_id, id),
    CONSTRAINT career_artifact_versions_number_ck CHECK (version_no >= 1),
    CONSTRAINT career_artifact_versions_content_ck CHECK (
        jsonb_typeof(content_json) = 'object'
        AND octet_length(content_json::text) <= 524288
        AND char_length(content_schema_version) BETWEEN 1 AND 80
    ),
    CONSTRAINT career_artifact_versions_template_ck CHECK (
        char_length(template_key) BETWEEN 1 AND 80
        AND char_length(template_version) BETWEEN 1 AND 40
    ),
    CONSTRAINT career_artifact_versions_model_ck CHECK (
        model_id = btrim(model_id) AND char_length(model_id) BETWEEN 1 AND 64
    ),
    CONSTRAINT career_artifact_versions_render_profile_ck CHECK (
        jsonb_typeof(render_profile_snapshot) = 'object'
        AND octet_length(render_profile_snapshot::text) <= 32768
    ),
    CONSTRAINT career_artifact_versions_storage_ck CHECK (
        storage_key ~ '^users/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/career-artifacts/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/versions/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/content[.](docx|pptx)$'
        AND mime_type IN (
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'application/vnd.openxmlformats-officedocument.presentationml.presentation'
        )
        AND size_bytes BETWEEN 1 AND 10485760
        AND checksum_sha256 ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX career_artifact_versions_owner_artifact_version_ix
    ON career_artifact_versions (user_id, career_artifact_id, version_no DESC, id DESC);
CREATE INDEX career_artifact_versions_owner_created_ix
    ON career_artifact_versions (user_id, career_artifact_id, created_at DESC, id DESC);

ALTER TABLE career_artifacts
    ADD CONSTRAINT career_artifacts_current_version_owner_fk
        FOREIGN KEY (user_id, id, current_version_id)
        REFERENCES career_artifact_versions(user_id, career_artifact_id, id)
        DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE career_artifact_evidence_links (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    artifact_version_id uuid NOT NULL,
    experience_item_id uuid NOT NULL,
    profile_evidence_id uuid NOT NULL,
    experience_version bigint NOT NULL,
    evidence_version bigint NOT NULL,
    usage_type varchar(30) NOT NULL,
    title_snapshot varchar(250) NOT NULL,
    content_snapshot varchar(20000) NOT NULL,
    snapshot_hash char(64) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT career_artifact_evidence_links_pk PRIMARY KEY (id),
    CONSTRAINT career_artifact_evidence_links_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT career_artifact_evidence_links_identity_uk UNIQUE (
        user_id, artifact_version_id, experience_item_id, profile_evidence_id, usage_type
    ),
    CONSTRAINT career_artifact_evidence_links_version_owner_fk
        FOREIGN KEY (user_id, artifact_version_id)
        REFERENCES career_artifact_versions(user_id, id) ON DELETE CASCADE,
    CONSTRAINT career_artifact_evidence_links_experience_owner_fk
        FOREIGN KEY (user_id, experience_item_id)
        REFERENCES experience_items(user_id, id),
    CONSTRAINT career_artifact_evidence_links_evidence_owner_fk
        FOREIGN KEY (user_id, profile_evidence_id)
        REFERENCES profile_evidence(user_id, id),
    CONSTRAINT career_artifact_evidence_links_version_ck CHECK (
        experience_version >= 0 AND evidence_version >= 0
    ),
    CONSTRAINT career_artifact_evidence_links_usage_ck CHECK (
        usage_type IN ('PRIMARY_EXPERIENCE', 'STRENGTH', 'SUPPORTING_FACT')
    ),
    CONSTRAINT career_artifact_evidence_links_title_ck CHECK (
        title_snapshot = btrim(title_snapshot)
        AND char_length(title_snapshot) BETWEEN 1 AND 250
        AND title_snapshot !~ '[[:cntrl:]]'
    ),
    CONSTRAINT career_artifact_evidence_links_content_ck CHECK (
        char_length(content_snapshot) BETWEEN 1 AND 20000
    ),
    CONSTRAINT career_artifact_evidence_links_hash_ck CHECK (
        snapshot_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX career_artifact_evidence_links_version_ix
    ON career_artifact_evidence_links (user_id, artifact_version_id, created_at, id);
CREATE INDEX career_artifact_evidence_links_experience_ix
    ON career_artifact_evidence_links (user_id, experience_item_id, created_at DESC);

CREATE TABLE career_artifact_generation_requests (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    career_artifact_id uuid NOT NULL,
    agent_run_id uuid NOT NULL,
    target_version_id uuid NOT NULL,
    render_profile_snapshot jsonb NOT NULL,
    render_profile_hash char(64) NOT NULL,
    created_at timestamptz NOT NULL,
    consumed_at timestamptz NULL,
    CONSTRAINT career_artifact_generation_requests_pk PRIMARY KEY (id),
    CONSTRAINT career_artifact_generation_requests_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT career_artifact_generation_requests_run_uk UNIQUE (user_id, agent_run_id),
    CONSTRAINT career_artifact_generation_requests_target_uk UNIQUE (user_id, target_version_id),
    CONSTRAINT career_artifact_generation_requests_artifact_owner_fk
        FOREIGN KEY (user_id, career_artifact_id)
        REFERENCES career_artifacts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT career_artifact_generation_requests_run_owner_fk
        FOREIGN KEY (user_id, agent_run_id) REFERENCES agent_runs(user_id, id),
    CONSTRAINT career_artifact_generation_requests_profile_ck CHECK (
        jsonb_typeof(render_profile_snapshot) = 'object'
        AND octet_length(render_profile_snapshot::text) <= 32768
        AND render_profile_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT career_artifact_generation_requests_time_ck CHECK (
        consumed_at IS NULL OR consumed_at >= created_at
    )
);

CREATE INDEX career_artifact_generation_requests_artifact_ix
    ON career_artifact_generation_requests (user_id, career_artifact_id, created_at DESC);
CREATE INDEX career_artifact_generation_requests_unconsumed_ix
    ON career_artifact_generation_requests (agent_run_id, created_at)
    WHERE consumed_at IS NULL;

CREATE TABLE career_artifact_object_deletion_outbox (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    career_artifact_id uuid NULL,
    artifact_version_id uuid NULL,
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
    CONSTRAINT career_artifact_object_deletion_outbox_pk PRIMARY KEY (id),
    CONSTRAINT career_artifact_object_deletion_outbox_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT career_artifact_object_deletion_outbox_user_id_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT career_artifact_object_deletion_outbox_artifact_fk
        FOREIGN KEY (user_id, career_artifact_id) REFERENCES career_artifacts(user_id, id),
    CONSTRAINT career_artifact_object_deletion_outbox_version_fk
        FOREIGN KEY (user_id, artifact_version_id)
        REFERENCES career_artifact_versions(user_id, id),
    CONSTRAINT career_artifact_object_deletion_outbox_reason_ck CHECK (
        reason IN ('ARTIFACT_DELETE', 'ORPHAN_UPLOAD_COMPENSATION')
    ),
    CONSTRAINT career_artifact_object_deletion_outbox_shape_ck CHECK (
        (reason = 'ARTIFACT_DELETE'
            AND career_artifact_id IS NOT NULL AND artifact_version_id IS NOT NULL)
        OR (reason = 'ORPHAN_UPLOAD_COMPENSATION'
            AND career_artifact_id IS NOT NULL AND artifact_version_id IS NULL)
    ),
    CONSTRAINT career_artifact_object_deletion_outbox_status_ck CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'DEAD')
    ),
    CONSTRAINT career_artifact_object_deletion_outbox_attempt_ck CHECK (
        attempt_count BETWEEN 0 AND 10
    ),
    CONSTRAINT career_artifact_object_deletion_outbox_claim_ck CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_token IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT career_artifact_object_deletion_outbox_terminal_ck CHECK (
        (status IN ('SUCCEEDED', 'DEAD') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'DEAD') AND completed_at IS NULL)
    ),
    CONSTRAINT career_artifact_object_deletion_outbox_storage_ck CHECK (
        storage_key ~ '^users/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/career-artifacts/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/versions/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/content[.](docx|pptx)$'
        AND storage_key LIKE
            'users/' || user_id::text || '/career-artifacts/'
            || career_artifact_id::text || '/versions/%'
    )
);

CREATE UNIQUE INDEX career_artifact_object_deletion_outbox_active_uk
    ON career_artifact_object_deletion_outbox (storage_key, reason)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX career_artifact_object_deletion_outbox_due_ix
    ON career_artifact_object_deletion_outbox (status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX career_artifact_object_deletion_outbox_artifact_ix
    ON career_artifact_object_deletion_outbox
        (user_id, career_artifact_id, created_at DESC);

ALTER TABLE agent_runs DROP CONSTRAINT agent_runs_workflow_type_ck;
ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_workflow_type_ck CHECK (workflow_type IN (
        'DOCUMENT_INGESTION', 'JOB_POSTING_EXTRACTION', 'JOB_ANALYSIS',
        'COVER_LETTER_GENERATION', 'COVER_LETTER_VERIFICATION',
        'INTERVIEW_PREPARATION', 'INTERVIEW_ANSWER_FEEDBACK', 'MOCK_INTERVIEW_FEEDBACK',
        'GITHUB_INGESTION', 'RESUME_GENERATION', 'PORTFOLIO_GENERATION'
    ));

ALTER TABLE agent_run_resource_links
    ADD COLUMN career_artifact_id uuid NULL,
    DROP CONSTRAINT agent_run_resource_links_exactly_one_ck,
    DROP CONSTRAINT agent_run_resource_links_kind_ck,
    DROP CONSTRAINT agent_run_resource_links_secondary_kind_ck,
    ADD CONSTRAINT agent_run_resource_links_career_artifact_owner_fk
        FOREIGN KEY (user_id, career_artifact_id) REFERENCES career_artifacts(user_id, id),
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
            github_source_id,
            career_artifact_id
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
        OR (resource_kind = 'CAREER_ARTIFACT' AND career_artifact_id IS NOT NULL)
    ),
    ADD CONSTRAINT agent_run_resource_links_secondary_kind_ck CHECK (
        resource_kind NOT IN (
            'JOB_ANALYSIS', 'COVER_LETTER_ANSWER_VERSION', 'RESEARCH_RUN'
        ) OR NOT primary_resource
    );

CREATE INDEX agent_run_resource_links_career_artifact_ix
    ON agent_run_resource_links (user_id, career_artifact_id, created_at DESC)
    WHERE career_artifact_id IS NOT NULL;

CREATE OR REPLACE FUNCTION assert_agent_run_document_resource_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM agent_run_resource_links link
        JOIN agent_runs run ON run.user_id = link.user_id AND run.id = link.agent_run_id
        LEFT JOIN career_artifacts artifact
          ON artifact.user_id = link.user_id AND artifact.id = link.career_artifact_id
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
              OR (link.resource_kind = 'CAREER_ARTIFACT'
                  AND (run.resource_type IS DISTINCT FROM 'CAREER_ARTIFACT'
                      OR run.resource_id IS DISTINCT FROM link.career_artifact_id
                      OR artifact.id IS NULL
                      OR (artifact.artifact_type = 'RESUME'
                          AND run.workflow_type IS DISTINCT FROM 'RESUME_GENERATION')
                      OR (artifact.artifact_type = 'PORTFOLIO'
                          AND run.workflow_type IS DISTINCT FROM 'PORTFOLIO_GENERATION')))
          )
    ) OR EXISTS (
        SELECT 1
        FROM agent_runs run
        WHERE run.resource_type IN (
            'DOCUMENT', 'JOB', 'COVER_LETTER', 'QUESTION_SET',
            'INTERVIEW_ANSWER_VERSION', 'GITHUB_SOURCE', 'CAREER_ARTIFACT'
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
                    OR (run.resource_type = 'CAREER_ARTIFACT'
                        AND run.workflow_type IN ('RESUME_GENERATION', 'PORTFOLIO_GENERATION')
                        AND link.resource_kind = 'CAREER_ARTIFACT'
                        AND link.career_artifact_id = run.resource_id)
                )
          )
    ) OR EXISTS (
        SELECT 1 FROM agent_runs run
        WHERE run.workflow_type = 'GITHUB_INGESTION'
          AND (run.resource_type IS DISTINCT FROM 'GITHUB_SOURCE' OR run.resource_id IS NULL)
    ) OR EXISTS (
        SELECT 1 FROM agent_runs run
        WHERE run.workflow_type IN ('RESUME_GENERATION', 'PORTFOLIO_GENERATION')
          AND (run.resource_type IS DISTINCT FROM 'CAREER_ARTIFACT' OR run.resource_id IS NULL)
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'agent_run_typed_resource_parity_ck',
            MESSAGE = 'resource projection must match the authoritative typed link and workflow';
    END IF;
    RETURN NULL;
END;
$$;

CREATE FUNCTION assert_career_artifact_state_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM career_artifacts artifact
        WHERE artifact.current_version_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM career_artifact_versions version
              WHERE version.user_id = artifact.user_id
                AND version.career_artifact_id = artifact.id
                AND version.id = artifact.current_version_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM career_artifacts artifact
        WHERE artifact.latest_agent_run_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM agent_run_resource_links link
              JOIN agent_runs run
                ON run.user_id = link.user_id AND run.id = link.agent_run_id
              WHERE link.user_id = artifact.user_id
                AND link.agent_run_id = artifact.latest_agent_run_id
                AND link.career_artifact_id = artifact.id
                AND link.resource_kind = 'CAREER_ARTIFACT'
                AND link.primary_resource
                AND ((artifact.artifact_type = 'RESUME'
                        AND run.workflow_type = 'RESUME_GENERATION')
                    OR (artifact.artifact_type = 'PORTFOLIO'
                        AND run.workflow_type = 'PORTFOLIO_GENERATION'))
          )
    ) OR EXISTS (
        SELECT 1
        FROM career_artifact_versions version
        JOIN career_artifacts artifact
          ON artifact.user_id = version.user_id AND artifact.id = version.career_artifact_id
        JOIN agent_runs run
          ON run.user_id = version.user_id AND run.id = version.agent_run_id
        WHERE run.resource_type IS DISTINCT FROM 'CAREER_ARTIFACT'
           OR run.resource_id IS DISTINCT FROM version.career_artifact_id
           OR version.storage_key IS DISTINCT FROM
                'users/' || version.user_id::text || '/career-artifacts/'
                || version.career_artifact_id::text || '/versions/'
                || version.id::text || '/content.'
                || CASE artifact.artifact_type
                    WHEN 'RESUME' THEN 'docx' ELSE 'pptx' END
           OR (artifact.artifact_type = 'RESUME'
                AND (run.workflow_type IS DISTINCT FROM 'RESUME_GENERATION'
                    OR version.mime_type IS DISTINCT FROM
                        'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
                    OR version.storage_key !~ '/content[.]docx$'
                    OR version.template_key IS DISTINCT FROM 'resume-ats-v1'
                    OR version.template_version IS DISTINCT FROM '1'))
           OR (artifact.artifact_type = 'PORTFOLIO'
                AND (run.workflow_type IS DISTINCT FROM 'PORTFOLIO_GENERATION'
                    OR version.mime_type IS DISTINCT FROM
                        'application/vnd.openxmlformats-officedocument.presentationml.presentation'
                    OR version.storage_key !~ '/content[.]pptx$'
                    OR version.template_key IS DISTINCT FROM 'portfolio-interview-v1'
                    OR version.template_version IS DISTINCT FROM '1'))
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'career_artifact_state_parity_ck',
            MESSAGE = 'career artifact current version, latest run, type, and file must agree';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER career_artifacts_state_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON career_artifacts
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_career_artifact_state_parity();

CREATE CONSTRAINT TRIGGER career_artifact_versions_state_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON career_artifact_versions
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_career_artifact_state_parity();

CREATE CONSTRAINT TRIGGER agent_links_career_artifact_state_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON agent_run_resource_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_career_artifact_state_parity();

CREATE FUNCTION assert_career_artifact_generation_request_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM agent_runs run
        WHERE run.workflow_type IN ('RESUME_GENERATION', 'PORTFOLIO_GENERATION')
          AND NOT EXISTS (
              SELECT 1
              FROM career_artifact_generation_requests request
              WHERE request.user_id = run.user_id
                AND request.agent_run_id = run.id
                AND request.career_artifact_id = run.resource_id
          )
    ) OR EXISTS (
        SELECT 1
        FROM career_artifact_generation_requests request
        JOIN agent_runs run
          ON run.user_id = request.user_id AND run.id = request.agent_run_id
        JOIN career_artifacts artifact
          ON artifact.user_id = request.user_id AND artifact.id = request.career_artifact_id
        WHERE run.resource_type IS DISTINCT FROM 'CAREER_ARTIFACT'
           OR run.resource_id IS DISTINCT FROM request.career_artifact_id
           OR (artifact.artifact_type = 'RESUME'
                AND run.workflow_type IS DISTINCT FROM 'RESUME_GENERATION')
           OR (artifact.artifact_type = 'PORTFOLIO'
                AND run.workflow_type IS DISTINCT FROM 'PORTFOLIO_GENERATION')
           OR (request.consumed_at IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM career_artifact_versions version
                WHERE version.user_id = request.user_id
                  AND version.id = request.target_version_id
                  AND version.career_artifact_id = request.career_artifact_id
                  AND version.agent_run_id = request.agent_run_id
           ))
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'career_artifact_generation_request_parity_ck',
            MESSAGE = 'career artifact workflow must own exactly one matching generation request';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER career_artifact_generation_requests_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON career_artifact_generation_requests
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_career_artifact_generation_request_parity();

CREATE CONSTRAINT TRIGGER agent_runs_career_artifact_request_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON agent_runs
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_career_artifact_generation_request_parity();

CREATE FUNCTION assert_career_artifact_evidence_link()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM experience_items experience
        JOIN profile_evidence evidence
          ON evidence.user_id = experience.user_id
         AND evidence.id = experience.canonical_evidence_id
        WHERE experience.user_id = NEW.user_id
          AND experience.id = NEW.experience_item_id
          AND evidence.id = NEW.profile_evidence_id
          AND evidence.source_type = 'EXPERIENCE'
          AND evidence.source_entity_id = experience.id
          AND experience.verification_status = 'VERIFIED'
          AND evidence.verification_status = 'VERIFIED'
          AND experience.deleted_at IS NULL
          AND experience.version = NEW.experience_version
          AND evidence.version = NEW.evidence_version
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'career_artifact_evidence_canonical_ck',
            MESSAGE = 'artifact provenance must reference the selected verified canonical experience';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER career_artifact_evidence_links_canonical_ct
AFTER INSERT ON career_artifact_evidence_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_career_artifact_evidence_link();

CREATE FUNCTION reject_career_artifact_evidence_link_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'career_artifact_evidence_link_immutable_ck',
        MESSAGE = 'career artifact provenance links are immutable';
END;
$$;

CREATE TRIGGER career_artifact_evidence_links_immutable_tr
BEFORE UPDATE ON career_artifact_evidence_links
FOR EACH ROW EXECUTE FUNCTION reject_career_artifact_evidence_link_update();

CREATE FUNCTION reject_career_artifact_version_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.render_profile_snapshot <> '{}'::jsonb
       AND NEW.render_profile_snapshot = '{}'::jsonb
       AND (to_jsonb(NEW) - 'render_profile_snapshot')
           IS NOT DISTINCT FROM (to_jsonb(OLD) - 'render_profile_snapshot')
       AND EXISTS (
           SELECT 1 FROM career_artifacts artifact
           WHERE artifact.user_id = OLD.user_id
             AND artifact.id = OLD.career_artifact_id
             AND artifact.deleted_at IS NOT NULL
       ) THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'career_artifact_version_immutable_ck',
        MESSAGE = 'career artifact versions are immutable';
END;
$$;

CREATE TRIGGER career_artifact_versions_immutable_tr
BEFORE UPDATE ON career_artifact_versions
FOR EACH ROW EXECUTE FUNCTION reject_career_artifact_version_update();

CREATE FUNCTION assert_career_artifact_version_sequence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    expected_version integer;
BEGIN
    SELECT COALESCE(MAX(version_no), 0) + 1
      INTO expected_version
      FROM career_artifact_versions
     WHERE user_id = NEW.user_id AND career_artifact_id = NEW.career_artifact_id;
    IF NEW.version_no <> expected_version THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'career_artifact_version_sequence_ck',
            MESSAGE = 'career artifact version numbers must increase from one';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER career_artifact_versions_sequence_tr
BEFORE INSERT ON career_artifact_versions
FOR EACH ROW EXECUTE FUNCTION assert_career_artifact_version_sequence();
