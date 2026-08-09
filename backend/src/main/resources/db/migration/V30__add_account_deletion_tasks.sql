CREATE TABLE account_deletion_tasks (
    id uuid NOT NULL,
    subject_user_id uuid NULL,
    status varchar(20) NOT NULL,
    policy_version varchar(50) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    claim_token uuid NULL,
    lease_expires_at timestamptz NULL,
    purge_by timestamptz NOT NULL,
    last_error_code varchar(100) NULL,
    requested_at timestamptz NOT NULL,
    completed_at timestamptz NULL,
    CONSTRAINT account_deletion_tasks_pk PRIMARY KEY (id),
    CONSTRAINT account_deletion_tasks_status_ck CHECK (
        status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'DEAD')
    ),
    CONSTRAINT account_deletion_tasks_policy_ck CHECK (
        policy_version = btrim(policy_version)
        AND char_length(policy_version) BETWEEN 1 AND 50
    ),
    CONSTRAINT account_deletion_tasks_attempt_ck CHECK (
        attempt_count BETWEEN 0 AND 20
    ),
    CONSTRAINT account_deletion_tasks_claim_ck CHECK (
        (status = 'RUNNING' AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'RUNNING' AND claim_token IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT account_deletion_tasks_terminal_ck CHECK (
        (status = 'SUCCEEDED' AND completed_at IS NOT NULL AND subject_user_id IS NULL)
        OR (status = 'DEAD' AND completed_at IS NOT NULL AND subject_user_id IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'DEAD') AND completed_at IS NULL AND subject_user_id IS NOT NULL)
    ),
    CONSTRAINT account_deletion_tasks_time_ck CHECK (
        next_attempt_at >= requested_at
        AND purge_by = requested_at + interval '24 hours'
        AND (completed_at IS NULL OR completed_at >= requested_at)
    )
);

CREATE UNIQUE INDEX account_deletion_tasks_active_subject_uk
    ON account_deletion_tasks (subject_user_id)
    WHERE subject_user_id IS NOT NULL AND status <> 'SUCCEEDED';
CREATE INDEX account_deletion_tasks_due_ix
    ON account_deletion_tasks (status, next_attempt_at, id)
    WHERE status IN ('QUEUED', 'RUNNING', 'RETRY_WAIT');
CREATE INDEX account_deletion_tasks_completed_cleanup_ix
    ON account_deletion_tasks (completed_at, id)
    WHERE status = 'SUCCEEDED';
