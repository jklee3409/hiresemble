CREATE TABLE job_auto_analysis_requests (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    job_version bigint NOT NULL,
    job_content_hash char(64) NOT NULL,
    quality_mode varchar(30) NOT NULL DEFAULT 'BALANCED',
    status varchar(30) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    claim_token uuid NULL,
    lease_expires_at timestamptz NULL,
    next_attempt_at timestamptz NOT NULL,
    agent_run_id uuid NULL,
    error_code varchar(100) NULL,
    error_message_safe varchar(500) NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    completed_at timestamptz NULL,
    CONSTRAINT job_auto_analysis_requests_pk PRIMARY KEY (id),
    CONSTRAINT job_auto_analysis_requests_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT job_auto_analysis_requests_job_revision_uk
        UNIQUE (user_id, job_posting_id, job_version),
    CONSTRAINT job_auto_analysis_requests_user_id_fk FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT job_auto_analysis_requests_job_owner_fk
        FOREIGN KEY (user_id, job_posting_id)
        REFERENCES job_postings(user_id, id) ON DELETE CASCADE,
    CONSTRAINT job_auto_analysis_requests_run_owner_fk
        FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id),
    CONSTRAINT job_auto_analysis_requests_version_ck CHECK (job_version >= 0),
    CONSTRAINT job_auto_analysis_requests_hash_ck
        CHECK (job_content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT job_auto_analysis_requests_quality_ck
        CHECK (quality_mode = 'BALANCED'),
    CONSTRAINT job_auto_analysis_requests_status_ck CHECK (
        status IN ('PENDING', 'CLAIMED', 'LAUNCHED', 'BLOCKED', 'SUPERSEDED')
    ),
    CONSTRAINT job_auto_analysis_requests_attempt_ck CHECK (attempt_count >= 0),
    CONSTRAINT job_auto_analysis_requests_error_ck CHECK (
        (error_code IS NULL AND error_message_safe IS NULL)
        OR (
            error_code ~ '^[A-Z][A-Z0-9_]{0,99}$'
            AND char_length(error_message_safe) BETWEEN 1 AND 500
            AND error_message_safe !~ '[\r\n]'
        )
    ),
    CONSTRAINT job_auto_analysis_requests_state_shape_ck CHECK (
        (status = 'PENDING'
            AND claim_token IS NULL AND lease_expires_at IS NULL
            AND agent_run_id IS NULL AND error_code IS NULL
            AND completed_at IS NULL)
        OR (status = 'CLAIMED'
            AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL
            AND agent_run_id IS NULL AND error_code IS NULL
            AND completed_at IS NULL)
        OR (status = 'LAUNCHED'
            AND claim_token IS NULL AND lease_expires_at IS NULL
            AND agent_run_id = id AND error_code IS NULL
            AND completed_at IS NOT NULL)
        OR (status = 'BLOCKED'
            AND claim_token IS NULL AND lease_expires_at IS NULL
            AND agent_run_id IS NULL AND error_code IS NOT NULL
            AND completed_at IS NOT NULL)
        OR (status = 'SUPERSEDED'
            AND claim_token IS NULL AND lease_expires_at IS NULL
            AND agent_run_id IS NULL AND error_code IS NULL
            AND completed_at IS NOT NULL)
    ),
    CONSTRAINT job_auto_analysis_requests_time_ck CHECK (
        updated_at >= created_at
        AND next_attempt_at >= created_at
        AND (lease_expires_at IS NULL OR lease_expires_at >= updated_at)
        AND (completed_at IS NULL OR completed_at >= created_at)
    )
);

CREATE INDEX job_auto_analysis_requests_due_ix
    ON job_auto_analysis_requests (next_attempt_at, created_at, id)
    WHERE status IN ('PENDING', 'CLAIMED');

CREATE INDEX job_auto_analysis_requests_job_latest_ix
    ON job_auto_analysis_requests (user_id, job_posting_id, job_version DESC);
