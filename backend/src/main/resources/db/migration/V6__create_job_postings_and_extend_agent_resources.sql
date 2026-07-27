CREATE TABLE companies (
    id uuid NOT NULL,
    normalized_name varchar(200) NOT NULL,
    display_name varchar(200) NOT NULL,
    official_website varchar(2000) NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT companies_pk PRIMARY KEY (id),
    CONSTRAINT companies_name_ck CHECK (
        normalized_name = btrim(normalized_name)
        AND display_name = btrim(display_name)
        AND char_length(normalized_name) BETWEEN 1 AND 200
        AND char_length(display_name) BETWEEN 1 AND 200
        AND normalized_name !~ '[[:cntrl:]]'
        AND display_name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT companies_website_ck CHECK (
        official_website IS NULL OR char_length(official_website) BETWEEN 1 AND 2000
    ),
    CONSTRAINT companies_time_ck CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX companies_normalized_name_uk ON companies (lower(normalized_name));

CREATE TABLE job_postings (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    company_id uuid NULL,
    source_url varchar(2000) NOT NULL,
    canonical_url varchar(2000) NOT NULL,
    title varchar(300) NULL,
    position_name varchar(300) NULL,
    role_category varchar(100) NULL,
    employment_type varchar(100) NULL,
    location varchar(200) NULL,
    description_text text NULL,
    description_source varchar(30) NULL,
    deadline_at timestamptz NULL,
    deadline_source varchar(30) NOT NULL,
    deadline_confidence numeric(4,3) NULL,
    status varchar(30) NOT NULL,
    extraction_status varchar(30) NOT NULL,
    submitted_at timestamptz NULL,
    closed_at timestamptz NULL,
    closed_reason varchar(30) NULL,
    content_hash char(64) NULL,
    latest_agent_run_id uuid NULL,
    company_user_override boolean NOT NULL DEFAULT false,
    title_user_override boolean NOT NULL DEFAULT false,
    position_user_override boolean NOT NULL DEFAULT false,
    deadline_user_override boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deleted_at timestamptz NULL,
    CONSTRAINT job_postings_pk PRIMARY KEY (id),
    CONSTRAINT job_postings_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT job_postings_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT job_postings_company_fk FOREIGN KEY (company_id)
        REFERENCES companies(id),
    CONSTRAINT job_postings_latest_run_owner_fk FOREIGN KEY (user_id, latest_agent_run_id)
        REFERENCES agent_runs(user_id, id),
    CONSTRAINT job_postings_source_url_ck CHECK (
        char_length(source_url) BETWEEN 1 AND 2000
        AND char_length(canonical_url) BETWEEN 1 AND 2000
    ),
    CONSTRAINT job_postings_title_ck CHECK (
        title IS NULL OR (
            title = btrim(title)
            AND char_length(title) BETWEEN 1 AND 300
            AND title !~ '[[:cntrl:]]'
        )
    ),
    CONSTRAINT job_postings_position_ck CHECK (
        position_name IS NULL OR (
            position_name = btrim(position_name)
            AND char_length(position_name) BETWEEN 1 AND 300
            AND position_name !~ '[[:cntrl:]]'
        )
    ),
    CONSTRAINT job_postings_role_ck CHECK (
        role_category IS NULL OR char_length(role_category) BETWEEN 1 AND 100
    ),
    CONSTRAINT job_postings_employment_ck CHECK (
        employment_type IS NULL OR char_length(employment_type) BETWEEN 1 AND 100
    ),
    CONSTRAINT job_postings_location_ck CHECK (
        location IS NULL OR char_length(location) BETWEEN 1 AND 200
    ),
    CONSTRAINT job_postings_description_ck CHECK (
        (description_text IS NULL AND description_source IS NULL)
        OR (
            description_text IS NOT NULL
            AND char_length(btrim(description_text)) BETWEEN 1 AND 200000
            AND description_source IN ('AUTO_EXTRACTED', 'USER_ENTERED')
        )
    ),
    CONSTRAINT job_postings_deadline_source_ck CHECK (
        deadline_source IN ('USER_ENTERED', 'AUTO_EXTRACTED', 'UNKNOWN')
    ),
    CONSTRAINT job_postings_deadline_shape_ck CHECK (
        (deadline_at IS NULL AND deadline_source = 'UNKNOWN' AND deadline_confidence IS NULL)
        OR (
            deadline_at IS NOT NULL
            AND deadline_source IN ('USER_ENTERED', 'AUTO_EXTRACTED')
            AND (
                (deadline_source = 'USER_ENTERED' AND deadline_confidence IS NULL)
                OR (deadline_source = 'AUTO_EXTRACTED'
                    AND deadline_confidence IS NOT NULL
                    AND deadline_confidence BETWEEN 0 AND 1)
            )
        )
    ),
    CONSTRAINT job_postings_status_ck CHECK (
        status IN ('IN_PROGRESS', 'SUBMITTED', 'CLOSED')
    ),
    CONSTRAINT job_postings_extraction_status_ck CHECK (
        extraction_status IN (
            'QUEUED', 'EXTRACTING', 'EXTRACTED',
            'MANUAL_INPUT_PROVIDED', 'NEEDS_MANUAL_INPUT', 'FAILED'
        )
    ),
    CONSTRAINT job_postings_closed_shape_ck CHECK (
        (status = 'CLOSED' AND closed_at IS NOT NULL
            AND closed_reason IN ('DEADLINE_PASSED', 'USER_CLOSED', 'URL_INACTIVE'))
        OR (status <> 'CLOSED' AND closed_at IS NULL AND closed_reason IS NULL)
    ),
    CONSTRAINT job_postings_content_hash_ck CHECK (
        content_hash IS NULL OR content_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT job_postings_version_ck CHECK (version >= 0),
    CONSTRAINT job_postings_time_ck CHECK (
        updated_at >= created_at
        AND (submitted_at IS NULL OR submitted_at >= created_at)
        AND (closed_at IS NULL OR closed_at >= created_at)
        AND (deleted_at IS NULL OR deleted_at >= created_at)
    )
);

CREATE UNIQUE INDEX job_postings_active_canonical_url_uk
    ON job_postings (user_id, canonical_url) WHERE deleted_at IS NULL;
CREATE INDEX job_postings_owner_created_ix
    ON job_postings (user_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX job_postings_owner_updated_ix
    ON job_postings (user_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX job_postings_owner_status_ix
    ON job_postings (user_id, status, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX job_postings_owner_extraction_ix
    ON job_postings (user_id, extraction_status, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX job_postings_owner_deadline_ix
    ON job_postings (user_id, deadline_at, id)
    WHERE deleted_at IS NULL AND deadline_at IS NOT NULL;
CREATE INDEX job_postings_scheduler_deadline_ix
    ON job_postings (deadline_at, id)
    WHERE deleted_at IS NULL AND status IN ('IN_PROGRESS', 'SUBMITTED');

CREATE TABLE job_status_history (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    from_status varchar(30) NULL,
    to_status varchar(30) NOT NULL,
    reason varchar(100) NOT NULL,
    changed_by varchar(20) NOT NULL,
    changed_at timestamptz NOT NULL,
    CONSTRAINT job_status_history_pk PRIMARY KEY (id),
    CONSTRAINT job_status_history_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT job_status_history_job_owner_fk FOREIGN KEY (user_id, job_posting_id)
        REFERENCES job_postings(user_id, id),
    CONSTRAINT job_status_history_from_ck CHECK (
        from_status IS NULL OR from_status IN ('IN_PROGRESS', 'SUBMITTED', 'CLOSED')
    ),
    CONSTRAINT job_status_history_to_ck CHECK (
        to_status IN ('IN_PROGRESS', 'SUBMITTED', 'CLOSED')
    ),
    CONSTRAINT job_status_history_transition_ck CHECK (
        from_status IS NULL OR from_status <> to_status
    ),
    CONSTRAINT job_status_history_reason_ck CHECK (
        reason = btrim(reason) AND char_length(reason) BETWEEN 1 AND 100
    ),
    CONSTRAINT job_status_history_changed_by_ck CHECK (
        changed_by IN ('USER', 'SCHEDULER', 'SYSTEM')
    )
);

CREATE INDEX job_status_history_job_changed_ix
    ON job_status_history (user_id, job_posting_id, changed_at DESC, id DESC);

ALTER TABLE agent_run_resource_links
    ALTER COLUMN document_id DROP NOT NULL,
    ADD COLUMN job_posting_id uuid NULL,
    DROP CONSTRAINT agent_run_resource_links_kind_ck,
    ADD CONSTRAINT agent_run_resource_links_job_owner_fk
        FOREIGN KEY (user_id, job_posting_id) REFERENCES job_postings(user_id, id),
    ADD CONSTRAINT agent_run_resource_links_exactly_one_ck CHECK (
        num_nonnulls(document_id, job_posting_id) = 1
    ),
    ADD CONSTRAINT agent_run_resource_links_kind_ck CHECK (
        (resource_kind = 'DOCUMENT' AND document_id IS NOT NULL AND job_posting_id IS NULL)
        OR (resource_kind = 'JOB' AND document_id IS NULL AND job_posting_id IS NOT NULL)
    );

CREATE INDEX agent_run_resource_links_job_ix
    ON agent_run_resource_links (user_id, job_posting_id, created_at DESC)
    WHERE job_posting_id IS NOT NULL;

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
              OR
              (link.resource_kind = 'JOB'
                  AND (run.resource_type IS DISTINCT FROM 'JOB'
                       OR run.resource_id IS DISTINCT FROM link.job_posting_id))
          )
    ) OR EXISTS (
        SELECT 1
        FROM agent_runs run
        WHERE run.resource_type IN ('DOCUMENT', 'JOB')
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
                    OR
                    (run.resource_type = 'JOB'
                        AND link.resource_kind = 'JOB'
                        AND link.job_posting_id = run.resource_id)
                )
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'agent_run_typed_resource_parity_ck',
            MESSAGE = 'resource projection must match the authoritative typed link';
    END IF;
    RETURN NULL;
END;
$$;

CREATE FUNCTION assert_job_latest_run_link()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM job_postings job
        WHERE job.latest_agent_run_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1
              FROM agent_run_resource_links link
              WHERE link.user_id = job.user_id
                AND link.agent_run_id = job.latest_agent_run_id
                AND link.job_posting_id = job.id
                AND link.resource_kind = 'JOB'
                AND link.primary_resource
          )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'job_postings_latest_run_link_ck',
            MESSAGE = 'latest job run must own the typed job resource link';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER job_postings_latest_run_link_ct
AFTER INSERT OR UPDATE OR DELETE ON job_postings
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_job_latest_run_link();

CREATE CONSTRAINT TRIGGER agent_links_latest_job_run_ct
AFTER INSERT OR UPDATE OR DELETE ON agent_run_resource_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_job_latest_run_link();
