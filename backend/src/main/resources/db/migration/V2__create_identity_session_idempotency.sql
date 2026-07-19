CREATE TABLE users (
    id uuid NOT NULL,
    email varchar(320) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(100) NOT NULL,
    role varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    terms_agreed_at timestamptz NOT NULL,
    ai_consent_at timestamptz NOT NULL,
    last_login_at timestamptz NULL,
    withdrawn_at timestamptz NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT users_pk PRIMARY KEY (id),
    CONSTRAINT users_email_uk UNIQUE (email),
    CONSTRAINT users_email_normalized_ck CHECK (
        email = lower(email)
        AND email = btrim(email)
        AND char_length(email) BETWEEN 3 AND 320
    ),
    CONSTRAINT users_display_name_length_ck CHECK (char_length(display_name) BETWEEN 1 AND 100),
    CONSTRAINT users_role_ck CHECK (role IN ('USER')),
    CONSTRAINT users_status_ck CHECK (status IN ('ACTIVE', 'LOCKED', 'WITHDRAWN')),
    CONSTRAINT users_withdrawn_at_ck CHECK (
        (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
        OR (status <> 'WITHDRAWN' AND withdrawn_at IS NULL)
    )
);

CREATE TABLE user_profiles (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    legal_name varchar(100) NULL,
    introduction varchar(2000) NULL,
    desired_roles jsonb NOT NULL DEFAULT '[]'::jsonb,
    desired_industries jsonb NOT NULL DEFAULT '[]'::jsonb,
    desired_locations jsonb NOT NULL DEFAULT '[]'::jsonb,
    expected_graduation_date date NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT user_profiles_pk PRIMARY KEY (id),
    CONSTRAINT user_profiles_user_id_uk UNIQUE (user_id),
    CONSTRAINT user_profiles_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT user_profiles_desired_roles_array_ck CHECK (jsonb_typeof(desired_roles) = 'array'),
    CONSTRAINT user_profiles_desired_industries_array_ck CHECK (jsonb_typeof(desired_industries) = 'array'),
    CONSTRAINT user_profiles_desired_locations_array_ck CHECK (jsonb_typeof(desired_locations) = 'array')
);

CREATE TABLE spring_session (
    primary_id char(36) NOT NULL,
    session_id char(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name varchar(100) NULL,
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id char(36) NOT NULL,
    attribute_name varchar(200) NOT NULL,
    attribute_bytes bytea NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk
        FOREIGN KEY (session_primary_id) REFERENCES spring_session(primary_id) ON DELETE CASCADE
);

CREATE TABLE idempotency_records (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    http_method varchar(10) NOT NULL,
    route_scope varchar(500) NOT NULL,
    resource_scope_id uuid NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    hash_key_version integer NOT NULL,
    state varchar(20) NOT NULL,
    response_status integer NULL,
    response_json jsonb NULL,
    resource_type varchar(50) NULL,
    resource_id uuid NULL,
    agent_run_id uuid NULL,
    created_at timestamptz NOT NULL,
    completed_at timestamptz NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT idempotency_records_pk PRIMARY KEY (id),
    CONSTRAINT idempotency_records_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT idempotency_records_scope_key_uk UNIQUE (
        user_id,
        http_method,
        route_scope,
        resource_scope_id,
        idempotency_key
    ),
    CONSTRAINT idempotency_records_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT idempotency_records_method_ck CHECK (http_method = upper(http_method)),
    CONSTRAINT idempotency_records_key_ck CHECK (
        idempotency_key ~ '^[A-Za-z0-9._:-]{8,128}$'
    ),
    CONSTRAINT idempotency_records_hash_ck CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT idempotency_records_hash_key_version_ck CHECK (hash_key_version > 0),
    CONSTRAINT idempotency_records_state_ck CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT idempotency_records_response_status_ck CHECK (
        response_status IS NULL OR response_status BETWEEN 100 AND 599
    ),
    CONSTRAINT idempotency_records_state_payload_ck CHECK (
        (
            state = 'IN_PROGRESS'
            AND response_status IS NULL
            AND response_json IS NULL
            AND completed_at IS NULL
        )
        OR (
            state = 'COMPLETED'
            AND response_status IS NOT NULL
            AND response_json IS NOT NULL
            AND completed_at IS NOT NULL
        )
    ),
    CONSTRAINT idempotency_records_expiry_ck CHECK (expires_at > created_at)
);

CREATE INDEX idempotency_records_expires_at_ix ON idempotency_records (expires_at);
CREATE INDEX idempotency_records_user_state_ix ON idempotency_records (user_id, state);
