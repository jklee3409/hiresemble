CREATE TABLE github_connection_attempts (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    session_binding_digest char(64) NOT NULL,
    state_digest char(64) NOT NULL,
    phase varchar(30) NOT NULL,
    pending_installation_id bigint NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT github_connection_attempts_pk PRIMARY KEY (id),
    CONSTRAINT github_connection_attempts_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_connection_attempts_state_digest_uk UNIQUE (state_digest),
    CONSTRAINT github_connection_attempts_user_id_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT github_connection_attempts_phase_ck CHECK (
        phase IN ('INSTALL_PENDING', 'OAUTH_PENDING')
    ),
    CONSTRAINT github_connection_attempts_installation_ck CHECK (
        (phase = 'INSTALL_PENDING' AND pending_installation_id IS NULL)
        OR (phase = 'OAUTH_PENDING' AND pending_installation_id > 0)
    ),
    CONSTRAINT github_connection_attempts_digest_ck CHECK (
        session_binding_digest ~ '^[0-9a-f]{64}$'
        AND state_digest ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT github_connection_attempts_time_ck CHECK (
        expires_at > created_at
        AND updated_at >= created_at
        AND (consumed_at IS NULL OR consumed_at >= created_at)
    )
);

CREATE INDEX github_connection_attempts_active_user_ix
    ON github_connection_attempts (user_id, expires_at, id)
    WHERE consumed_at IS NULL;
CREATE INDEX github_connection_attempts_cleanup_ix
    ON github_connection_attempts (expires_at, id);

CREATE TABLE github_app_connections (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    github_installation_id bigint NOT NULL,
    target_account_id bigint NOT NULL,
    target_account_login varchar(100) NOT NULL,
    target_account_type varchar(20) NOT NULL,
    repository_selection varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    permission_snapshot jsonb NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    connected_at timestamptz NOT NULL,
    verified_at timestamptz NOT NULL,
    last_checked_at timestamptz NOT NULL,
    disconnected_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT github_app_connections_pk PRIMARY KEY (id),
    CONSTRAINT github_app_connections_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_app_connections_installation_uk UNIQUE (github_installation_id),
    CONSTRAINT github_app_connections_user_id_fk
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT github_app_connections_identity_ck CHECK (
        github_installation_id > 0
        AND target_account_id > 0
        AND target_account_login = btrim(target_account_login)
        AND char_length(target_account_login) BETWEEN 1 AND 100
        AND target_account_type IN ('USER', 'ORGANIZATION')
    ),
    CONSTRAINT github_app_connections_selection_ck CHECK (
        repository_selection IN ('ALL', 'SELECTED')
    ),
    CONSTRAINT github_app_connections_status_ck CHECK (
        status IN ('ACTIVE', 'SUSPENDED', 'DISCONNECTING', 'DISCONNECTED', 'REVOKED')
    ),
    CONSTRAINT github_app_connections_permission_ck CHECK (
        permission_snapshot = '{"contents":"read","metadata":"read"}'::jsonb
    ),
    CONSTRAINT github_app_connections_version_ck CHECK (version >= 0),
    CONSTRAINT github_app_connections_time_ck CHECK (
        verified_at >= connected_at
        AND last_checked_at >= connected_at
        AND updated_at >= created_at
        AND ((status = 'DISCONNECTED' AND disconnected_at IS NOT NULL)
            OR (status <> 'DISCONNECTED' AND disconnected_at IS NULL))
    )
);

CREATE INDEX github_app_connections_user_status_ix
    ON github_app_connections (user_id, status, updated_at DESC, id);

ALTER TABLE github_sources
    ADD COLUMN access_mode varchar(20) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN github_app_connection_id uuid NULL,
    ADD CONSTRAINT github_sources_connection_owner_fk
        FOREIGN KEY (user_id, github_app_connection_id)
        REFERENCES github_app_connections(user_id, id),
    ADD CONSTRAINT github_sources_access_mode_ck CHECK (
        access_mode IN ('PUBLIC', 'GITHUB_APP')
    ),
    ADD CONSTRAINT github_sources_connection_shape_ck CHECK (
        (access_mode = 'PUBLIC' AND github_app_connection_id IS NULL)
        OR (access_mode = 'GITHUB_APP' AND github_app_connection_id IS NOT NULL)
    );

ALTER TABLE github_repositories
    DROP CONSTRAINT github_repositories_public_ck,
    ADD COLUMN visibility varchar(10) NOT NULL DEFAULT 'PUBLIC',
    ADD CONSTRAINT github_repositories_visibility_ck CHECK (
        (visibility = 'PUBLIC' AND NOT is_private)
        OR (visibility = 'PRIVATE' AND is_private)
    );

CREATE TABLE github_app_connection_repository_access (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    github_app_connection_id uuid NOT NULL,
    github_repository_id uuid NOT NULL,
    external_repository_id bigint NOT NULL,
    available boolean NOT NULL DEFAULT true,
    checked_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT github_app_connection_repository_access_pk PRIMARY KEY (id),
    CONSTRAINT github_app_connection_repository_access_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_app_connection_repository_access_identity_uk
        UNIQUE (user_id, github_app_connection_id, github_repository_id),
    CONSTRAINT github_app_connection_repository_access_external_uk
        UNIQUE (user_id, github_app_connection_id, external_repository_id),
    CONSTRAINT github_app_connection_repository_access_connection_fk
        FOREIGN KEY (user_id, github_app_connection_id)
        REFERENCES github_app_connections(user_id, id),
    CONSTRAINT github_app_connection_repository_access_repository_fk
        FOREIGN KEY (user_id, github_repository_id)
        REFERENCES github_repositories(user_id, id),
    CONSTRAINT github_app_connection_repository_access_external_ck CHECK (
        external_repository_id > 0
    ),
    CONSTRAINT github_app_connection_repository_access_time_ck CHECK (
        updated_at >= created_at AND checked_at >= created_at
    )
);

CREATE INDEX github_app_connection_repository_access_connection_ix
    ON github_app_connection_repository_access
        (user_id, github_app_connection_id, available, checked_at DESC);

ALTER TABLE github_repository_snapshots
    ADD COLUMN access_mode varchar(20) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN github_app_connection_id uuid NULL,
    ADD CONSTRAINT github_repository_snapshots_connection_owner_fk
        FOREIGN KEY (user_id, github_app_connection_id)
        REFERENCES github_app_connections(user_id, id),
    ADD CONSTRAINT github_repository_snapshots_access_mode_ck CHECK (
        (access_mode = 'PUBLIC' AND github_app_connection_id IS NULL)
        OR (access_mode = 'GITHUB_APP' AND github_app_connection_id IS NOT NULL)
    );

CREATE TABLE github_installation_revocation_outbox (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    github_app_connection_id uuid NOT NULL,
    github_installation_id bigint NOT NULL,
    reason varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    claim_token uuid NULL,
    lease_expires_at timestamptz NULL,
    last_error_code varchar(100) NULL,
    created_at timestamptz NOT NULL,
    completed_at timestamptz NULL,
    CONSTRAINT github_installation_revocation_outbox_pk PRIMARY KEY (id),
    CONSTRAINT github_installation_revocation_outbox_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT github_installation_revocation_outbox_connection_fk
        FOREIGN KEY (user_id, github_app_connection_id)
        REFERENCES github_app_connections(user_id, id),
    CONSTRAINT github_installation_revocation_outbox_installation_ck CHECK (
        github_installation_id > 0
    ),
    CONSTRAINT github_installation_revocation_outbox_reason_ck CHECK (
        reason IN ('USER_DISCONNECT', 'ACCOUNT_DELETION')
    ),
    CONSTRAINT github_installation_revocation_outbox_status_ck CHECK (
        status IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'SUCCEEDED', 'DEAD')
    ),
    CONSTRAINT github_installation_revocation_outbox_attempt_ck CHECK (
        attempt_count BETWEEN 0 AND 10
    ),
    CONSTRAINT github_installation_revocation_outbox_claim_ck CHECK (
        (status = 'RUNNING' AND claim_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'RUNNING' AND claim_token IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT github_installation_revocation_outbox_terminal_ck CHECK (
        (status IN ('SUCCEEDED', 'DEAD') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'DEAD') AND completed_at IS NULL)
    )
);

CREATE UNIQUE INDEX github_installation_revocation_outbox_active_uk
    ON github_installation_revocation_outbox (github_app_connection_id)
    WHERE status IN ('PENDING', 'RUNNING', 'RETRY_WAIT');
CREATE INDEX github_installation_revocation_outbox_due_ix
    ON github_installation_revocation_outbox (status, next_attempt_at, id)
    WHERE status IN ('PENDING', 'RUNNING', 'RETRY_WAIT');

ALTER TABLE github_snapshot_object_deletion_outbox
    DROP CONSTRAINT github_snapshot_object_deletion_outbox_reason_ck,
    ADD CONSTRAINT github_snapshot_object_deletion_outbox_reason_ck CHECK (
        reason IN (
            'SOURCE_DELETE', 'ORPHAN_SNAPSHOT_COMPENSATION',
            'PRIVATE_CONNECTION_DISCONNECT', 'ACCOUNT_DELETION'
        )
    );

ALTER TABLE profile_evidence
    DROP CONSTRAINT profile_evidence_source_shape_ck,
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
            AND verification_status <> 'SOURCE_DELETED'
            AND source_entity_id = github_repository_id
            AND document_id IS NULL
            AND github_source_id IS NOT NULL
            AND github_repository_id IS NOT NULL
            AND github_snapshot_id IS NOT NULL
            AND github_claim_key ~ '^[0-9a-f]{64}$')
        OR (source_type = 'GITHUB_REPOSITORY'
            AND verification_status = 'SOURCE_DELETED'
            AND source_entity_id IS NULL
            AND document_id IS NULL
            AND num_nonnulls(
                github_source_id, github_repository_id, github_snapshot_id, github_claim_key
            ) = 0)
        OR (source_type = 'MANUAL'
            AND source_entity_id IS NULL
            AND document_id IS NULL)
    );

CREATE OR REPLACE FUNCTION assert_github_evidence_primary_unit()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM profile_evidence evidence
        WHERE evidence.source_type = 'GITHUB_REPOSITORY'
          AND evidence.verification_status <> 'SOURCE_DELETED'
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
            MESSAGE = 'active GitHub evidence must have one owner-matched primary source unit';
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION reject_github_snapshot_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    deletion_ready boolean;
BEGIN
    IF TG_OP = 'DELETE' AND TG_TABLE_NAME = 'github_source_units' THEN
        SELECT EXISTS (
            SELECT 1
            FROM github_repository_snapshots snapshot
            JOIN github_snapshot_object_deletion_outbox deletion
              ON deletion.storage_key = snapshot.snapshot_storage_key
             AND deletion.status = 'SUCCEEDED'
            WHERE snapshot.user_id = OLD.user_id
              AND snapshot.id = OLD.snapshot_id
        ) INTO deletion_ready;
        IF deletion_ready THEN
            RETURN OLD;
        END IF;
    ELSIF TG_OP = 'DELETE' AND TG_TABLE_NAME = 'github_repository_snapshots' THEN
        SELECT EXISTS (
            SELECT 1
            FROM github_snapshot_object_deletion_outbox deletion
            WHERE deletion.storage_key = OLD.snapshot_storage_key
              AND deletion.status = 'SUCCEEDED'
        ) INTO deletion_ready;
        IF deletion_ready THEN
            RETURN OLD;
        END IF;
    END IF;

    RAISE EXCEPTION USING
        ERRCODE = '23514',
        CONSTRAINT = 'github_snapshot_immutable_ck',
        MESSAGE = 'GitHub snapshots and source units are immutable until object deletion succeeds';
END;
$$;

CREATE FUNCTION assert_github_private_repository_access_parity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM github_source_repository_links link
        JOIN github_sources source
          ON source.user_id = link.user_id AND source.id = link.github_source_id
        JOIN github_repositories repository
          ON repository.user_id = link.user_id AND repository.id = link.github_repository_id
        WHERE (source.access_mode = 'PUBLIC' AND repository.is_private)
           OR (source.access_mode = 'GITHUB_APP' AND NOT EXISTS (
                SELECT 1
                FROM github_app_connection_repository_access access
                WHERE access.user_id = source.user_id
                  AND access.github_app_connection_id = source.github_app_connection_id
                  AND access.github_repository_id = repository.id
                  AND access.external_repository_id = repository.external_repository_id
                  AND access.available
           ))
    ) OR EXISTS (
        SELECT 1
        FROM github_app_connection_repository_access access
        JOIN github_repositories repository
          ON repository.user_id = access.user_id
         AND repository.id = access.github_repository_id
        WHERE access.external_repository_id <> repository.external_repository_id
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'github_private_repository_access_parity_ck',
            MESSAGE = 'GitHub source access mode, connection, and external repository identity must agree';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER github_source_links_private_access_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON github_source_repository_links
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_private_repository_access_parity();

CREATE CONSTRAINT TRIGGER github_sources_private_access_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON github_sources
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_private_repository_access_parity();

CREATE CONSTRAINT TRIGGER github_repositories_private_access_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON github_repositories
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_private_repository_access_parity();

CREATE CONSTRAINT TRIGGER github_connection_repository_access_parity_ct
AFTER INSERT OR UPDATE OR DELETE ON github_app_connection_repository_access
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION assert_github_private_repository_access_parity();
