CREATE FUNCTION prevent_immutable_ai_configuration_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'immutable AI configuration cannot be changed'
        USING ERRCODE = '23514';
END;
$$;

CREATE FUNCTION prevent_immutable_ai_policy_content_change()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE'
       OR (to_jsonb(NEW) - TG_ARGV[0]) IS DISTINCT FROM (to_jsonb(OLD) - TG_ARGV[0]) THEN
        RAISE EXCEPTION 'immutable AI policy content cannot be changed'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TABLE ai_model_policies (
    id uuid NOT NULL,
    version bigint NOT NULL,
    policy_json jsonb NOT NULL,
    active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    CONSTRAINT ai_model_policies_pk PRIMARY KEY (id),
    CONSTRAINT ai_model_policies_version_uk UNIQUE (version),
    CONSTRAINT ai_model_policies_version_ck CHECK (version >= 1),
    CONSTRAINT ai_model_policies_policy_json_ck CHECK (jsonb_typeof(policy_json) = 'object')
);

CREATE UNIQUE INDEX ai_model_policies_active_uk
    ON ai_model_policies (active) WHERE active;

CREATE TRIGGER ai_model_policies_immutable_trg
BEFORE UPDATE OR DELETE ON ai_model_policies
FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ai_policy_content_change('active');

CREATE TABLE embedding_policy_versions (
    id uuid NOT NULL,
    version bigint NOT NULL,
    provider_key varchar(100) NOT NULL,
    product_key varchar(150) NOT NULL,
    dimension integer NOT NULL,
    distance_metric varchar(20) NOT NULL,
    generation integer NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    CONSTRAINT embedding_policy_versions_pk PRIMARY KEY (id),
    CONSTRAINT embedding_policy_versions_version_uk UNIQUE (version),
    CONSTRAINT embedding_policy_versions_version_ck CHECK (version >= 1),
    CONSTRAINT embedding_policy_versions_dimension_ck CHECK (dimension >= 1),
    CONSTRAINT embedding_policy_versions_metric_ck CHECK (distance_metric IN ('COSINE')),
    CONSTRAINT embedding_policy_versions_generation_ck CHECK (generation >= 1)
);

CREATE TRIGGER embedding_policy_versions_immutable_trg
BEFORE UPDATE OR DELETE ON embedding_policy_versions
FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ai_policy_content_change('enabled');

CREATE UNIQUE INDEX embedding_policy_versions_enabled_uk
    ON embedding_policy_versions (enabled) WHERE enabled;

CREATE TABLE ai_budget_policy_versions (
    id uuid NOT NULL,
    version bigint NOT NULL,
    user_default_daily_budget_usd numeric(12,6) NOT NULL,
    system_max_daily_budget_usd numeric(12,6) NOT NULL,
    async_run_max_cost_usd numeric(12,6) NOT NULL,
    mock_turn_max_cost_usd numeric(12,6) NOT NULL,
    mock_session_max_cost_usd numeric(12,6) NOT NULL,
    reset_zone varchar(50) NOT NULL,
    active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    CONSTRAINT ai_budget_policy_versions_pk PRIMARY KEY (id),
    CONSTRAINT ai_budget_policy_versions_version_uk UNIQUE (version),
    CONSTRAINT ai_budget_policy_versions_version_ck CHECK (version >= 1),
    CONSTRAINT ai_budget_policy_versions_user_default_ck CHECK (user_default_daily_budget_usd >= 0),
    CONSTRAINT ai_budget_policy_versions_system_max_ck CHECK (system_max_daily_budget_usd >= 0),
    CONSTRAINT ai_budget_policy_versions_async_run_ck CHECK (async_run_max_cost_usd >= 0),
    CONSTRAINT ai_budget_policy_versions_mock_turn_ck CHECK (mock_turn_max_cost_usd >= 0),
    CONSTRAINT ai_budget_policy_versions_mock_session_ck CHECK (mock_session_max_cost_usd >= 0),
    CONSTRAINT ai_budget_policy_versions_order_ck CHECK (
        user_default_daily_budget_usd <= system_max_daily_budget_usd
        AND async_run_max_cost_usd <= system_max_daily_budget_usd
        AND mock_turn_max_cost_usd <= mock_session_max_cost_usd
    ),
    CONSTRAINT ai_budget_policy_versions_reset_zone_ck CHECK (reset_zone = 'Asia/Seoul')
);

CREATE UNIQUE INDEX ai_budget_policy_versions_active_uk
    ON ai_budget_policy_versions (active) WHERE active;

CREATE TRIGGER ai_budget_policy_versions_immutable_trg
BEFORE UPDATE OR DELETE ON ai_budget_policy_versions
FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ai_policy_content_change('active');

INSERT INTO ai_budget_policy_versions (
    id, version, user_default_daily_budget_usd, system_max_daily_budget_usd,
    async_run_max_cost_usd, mock_turn_max_cost_usd, mock_session_max_cost_usd,
    reset_zone, active, created_at
) VALUES (
    '00000000-0000-0000-0000-000000000301', 1,
    1.000000, 2.000000, 0.300000, 0.030000, 0.300000,
    'Asia/Seoul', true, CURRENT_TIMESTAMP
);

CREATE TABLE user_ai_preferences (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    budget_policy_version bigint NOT NULL,
    default_quality_mode varchar(30) NOT NULL,
    high_quality_enabled boolean NOT NULL DEFAULT false,
    daily_budget_usd numeric(12,6) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT user_ai_preferences_pk PRIMARY KEY (id),
    CONSTRAINT user_ai_preferences_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT user_ai_preferences_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT user_ai_preferences_budget_policy_fk FOREIGN KEY (budget_policy_version)
        REFERENCES ai_budget_policy_versions(version),
    CONSTRAINT user_ai_preferences_quality_ck CHECK (default_quality_mode IN ('ECONOMY', 'BALANCED')),
    CONSTRAINT user_ai_preferences_daily_budget_ck CHECK (daily_budget_usd >= 0),
    CONSTRAINT user_ai_preferences_version_ck CHECK (version >= 0)
);

CREATE UNIQUE INDEX user_ai_preferences_active_user_uk
    ON user_ai_preferences (user_id) WHERE active;

INSERT INTO user_ai_preferences (
    id, user_id, budget_policy_version, default_quality_mode, high_quality_enabled,
    daily_budget_usd, active, version, created_at, updated_at
)
SELECT
    gen_random_uuid(), users.id, policy.version, 'ECONOMY', false,
    policy.user_default_daily_budget_usd, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users
CROSS JOIN ai_budget_policy_versions policy
WHERE policy.active;

CREATE TABLE ai_price_versions (
    id uuid NOT NULL,
    version bigint NOT NULL,
    catalog_key varchar(100) NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ai_price_versions_pk PRIMARY KEY (id),
    CONSTRAINT ai_price_versions_version_uk UNIQUE (version),
    CONSTRAINT ai_price_versions_catalog_version_uk UNIQUE (catalog_key, version),
    CONSTRAINT ai_price_versions_version_ck CHECK (version >= 1),
    CONSTRAINT ai_price_versions_effective_range_ck CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);

CREATE TRIGGER ai_price_versions_immutable_trg
BEFORE UPDATE OR DELETE ON ai_price_versions
FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ai_configuration_change();

CREATE TABLE ai_price_items (
    id uuid NOT NULL,
    price_version bigint NOT NULL,
    provider_key varchar(100) NOT NULL,
    product_key varchar(150) NOT NULL,
    unit varchar(50) NOT NULL,
    unit_size bigint NOT NULL,
    unit_price_usd numeric(12,6) NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ai_price_items_pk PRIMARY KEY (id),
    CONSTRAINT ai_price_items_version_id_uk UNIQUE (price_version, id),
    CONSTRAINT ai_price_items_version_item_uk UNIQUE (price_version, provider_key, product_key, unit),
    CONSTRAINT ai_price_items_price_version_fk FOREIGN KEY (price_version)
        REFERENCES ai_price_versions(version),
    CONSTRAINT ai_price_items_unit_ck CHECK (unit IN (
        'CHAT_INPUT_TOKEN', 'CHAT_CACHED_INPUT_TOKEN', 'CHAT_OUTPUT_TOKEN',
        'EMBEDDING_INPUT_TOKEN', 'SEARCH_BASIC_REQUEST', 'SEARCH_ADVANCED_REQUEST'
    )),
    CONSTRAINT ai_price_items_unit_size_ck CHECK (unit_size >= 1),
    CONSTRAINT ai_price_items_unit_price_ck CHECK (unit_price_usd >= 0)
);

CREATE TRIGGER ai_price_items_immutable_trg
BEFORE UPDATE OR DELETE ON ai_price_items
FOR EACH ROW EXECUTE FUNCTION prevent_immutable_ai_configuration_change();

CREATE TABLE agent_runs (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    workflow_type varchar(50) NOT NULL,
    status varchar(30) NOT NULL,
    current_step varchar(100) NULL,
    progress_percent integer NOT NULL DEFAULT 0,
    workflow_version varchar(50) NOT NULL,
    canonical_input_hash char(64) NOT NULL,
    input_reference_snapshot jsonb NOT NULL,
    budget_policy_version bigint NOT NULL,
    price_version bigint NULL,
    requested_quality_mode varchar(30) NULL,
    highest_model_tier_used varchar(30) NULL,
    estimated_cost_usd numeric(12,6) NOT NULL DEFAULT 0,
    reserved_cost_usd numeric(12,6) NOT NULL DEFAULT 0,
    actual_cost_usd numeric(12,6) NOT NULL DEFAULT 0,
    resource_type varchar(50) NULL,
    resource_id uuid NULL,
    retry_of_run_id uuid NULL,
    root_run_id uuid NOT NULL,
    run_attempt_no integer NOT NULL DEFAULT 1,
    retryable_failure boolean NOT NULL DEFAULT false,
    error_code varchar(100) NULL,
    error_message_safe varchar(500) NULL,
    partial_result_json jsonb NULL,
    claim_token uuid NULL,
    claimed_by varchar(150) NULL,
    lease_expires_at timestamptz NULL,
    heartbeat_at timestamptz NULL,
    cancel_requested_at timestamptz NULL,
    waiting_action_type varchar(50) NULL,
    waiting_action_route varchar(500) NULL,
    waiting_action_message varchar(500) NULL,
    state_version bigint NOT NULL DEFAULT 0,
    queued_at timestamptz NOT NULL,
    started_at timestamptz NULL,
    completed_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT agent_runs_pk PRIMARY KEY (id),
    CONSTRAINT agent_runs_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT agent_runs_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT agent_runs_budget_policy_fk FOREIGN KEY (budget_policy_version)
        REFERENCES ai_budget_policy_versions(version),
    CONSTRAINT agent_runs_price_version_fk FOREIGN KEY (price_version)
        REFERENCES ai_price_versions(version),
    CONSTRAINT agent_runs_retry_owner_fk FOREIGN KEY (user_id, retry_of_run_id)
        REFERENCES agent_runs(user_id, id),
    CONSTRAINT agent_runs_root_owner_fk FOREIGN KEY (user_id, root_run_id)
        REFERENCES agent_runs(user_id, id),
    CONSTRAINT agent_runs_workflow_type_ck CHECK (workflow_type IN (
        'DOCUMENT_INGESTION', 'JOB_POSTING_EXTRACTION', 'JOB_ANALYSIS',
        'COVER_LETTER_GENERATION', 'COVER_LETTER_VERIFICATION',
        'INTERVIEW_PREPARATION', 'INTERVIEW_ANSWER_FEEDBACK', 'MOCK_INTERVIEW_FEEDBACK'
    )),
    CONSTRAINT agent_runs_status_ck CHECK (status IN (
        'QUEUED', 'RUNNING', 'WAITING_USER', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED'
    )),
    CONSTRAINT agent_runs_progress_ck CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT agent_runs_input_hash_ck CHECK (canonical_input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT agent_runs_input_refs_ck CHECK (jsonb_typeof(input_reference_snapshot) = 'object'),
    CONSTRAINT agent_runs_quality_ck CHECK (
        requested_quality_mode IS NULL OR requested_quality_mode IN ('ECONOMY', 'BALANCED', 'HIGH_QUALITY')
    ),
    CONSTRAINT agent_runs_model_tier_ck CHECK (
        highest_model_tier_used IS NULL OR highest_model_tier_used IN ('LOW_COST', 'BALANCED', 'HIGH_QUALITY')
    ),
    CONSTRAINT agent_runs_cost_ck CHECK (
        estimated_cost_usd >= 0 AND reserved_cost_usd >= 0 AND actual_cost_usd >= 0
    ),
    CONSTRAINT agent_runs_paid_price_ck CHECK (
        (estimated_cost_usd = 0 AND reserved_cost_usd = 0 AND actual_cost_usd = 0)
        OR price_version IS NOT NULL
    ),
    CONSTRAINT agent_runs_resource_pair_ck CHECK (
        (resource_type IS NULL AND resource_id IS NULL)
        OR (resource_type IS NOT NULL AND resource_id IS NOT NULL)
    ),
    CONSTRAINT agent_runs_lineage_ck CHECK (
        run_attempt_no >= 1
        AND ((retry_of_run_id IS NULL AND run_attempt_no = 1 AND root_run_id = id)
          OR (retry_of_run_id IS NOT NULL AND run_attempt_no >= 2 AND root_run_id <> id))
    ),
    CONSTRAINT agent_runs_safe_error_pair_ck CHECK (
        (error_code IS NULL AND error_message_safe IS NULL)
        OR (error_code IS NOT NULL AND error_message_safe IS NOT NULL)
    ),
    CONSTRAINT agent_runs_partial_result_ck CHECK (
        partial_result_json IS NULL OR jsonb_typeof(partial_result_json) = 'object'
    ),
    CONSTRAINT agent_runs_claim_shape_ck CHECK (
        (status = 'RUNNING' AND claim_token IS NOT NULL AND claimed_by IS NOT NULL
            AND lease_expires_at IS NOT NULL AND heartbeat_at IS NOT NULL)
        OR (status <> 'RUNNING' AND claim_token IS NULL AND claimed_by IS NULL
            AND lease_expires_at IS NULL AND heartbeat_at IS NULL)
    ),
    CONSTRAINT agent_runs_waiting_shape_ck CHECK (
        (status = 'WAITING_USER' AND waiting_action_type IS NOT NULL AND waiting_action_message IS NOT NULL)
        OR (status <> 'WAITING_USER' AND waiting_action_type IS NULL
            AND waiting_action_route IS NULL AND waiting_action_message IS NULL)
    ),
    CONSTRAINT agent_runs_waiting_action_ck CHECK (
        waiting_action_type IS NULL OR waiting_action_type IN (
            'PROVIDE_DOCUMENT_TEXT', 'PROVIDE_JOB_TEXT', 'ENABLE_HIGH_QUALITY', 'INCREASE_BUDGET'
        )
    ),
    CONSTRAINT agent_runs_terminal_timestamp_ck CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED') AND completed_at IS NULL)
    ),
    CONSTRAINT agent_runs_nonactive_reserve_ck CHECK (
        status IN ('QUEUED', 'RUNNING') OR reserved_cost_usd = 0
    ),
    CONSTRAINT agent_runs_state_version_ck CHECK (state_version >= 0),
    CONSTRAINT agent_runs_time_order_ck CHECK (
        updated_at >= queued_at
        AND (started_at IS NULL OR started_at >= queued_at)
        AND (completed_at IS NULL OR completed_at >= queued_at)
        AND (heartbeat_at IS NULL OR heartbeat_at >= queued_at)
        AND (lease_expires_at IS NULL OR lease_expires_at > heartbeat_at)
    )
);

CREATE UNIQUE INDEX agent_runs_retry_predecessor_uk
    ON agent_runs (user_id, retry_of_run_id)
    WHERE retry_of_run_id IS NOT NULL;

CREATE INDEX agent_runs_owner_queued_ix ON agent_runs (user_id, queued_at DESC);
CREATE INDEX agent_runs_dispatch_ix ON agent_runs (status, queued_at, id);
CREATE INDEX agent_runs_stale_lease_ix ON agent_runs (lease_expires_at) WHERE status = 'RUNNING';

CREATE FUNCTION prevent_terminal_agent_run_reopen()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status IN ('SUCCEEDED', 'FAILED', 'CANCELLED', 'INTERRUPTED')
       AND NEW.status <> OLD.status THEN
        RAISE EXCEPTION 'terminal agent run cannot transition'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER agent_runs_terminal_immutable_trg
BEFORE UPDATE ON agent_runs
FOR EACH ROW EXECUTE FUNCTION prevent_terminal_agent_run_reopen();

CREATE TABLE agent_steps (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    agent_run_id uuid NOT NULL,
    step_key varchar(100) NOT NULL,
    scope_key varchar(100) NULL,
    step_order integer NOT NULL,
    agent_name varchar(150) NOT NULL,
    status varchar(30) NOT NULL,
    attempt integer NOT NULL,
    max_attempts integer NOT NULL,
    input_hash char(64) NOT NULL,
    output_hash char(64) NULL,
    input_refs jsonb NOT NULL,
    output_json jsonb NULL,
    output_schema_version varchar(50) NOT NULL,
    model_policy_version bigint NOT NULL,
    prompt_version varchar(100) NOT NULL,
    requested_quality_mode varchar(30) NULL,
    model_tier_used varchar(30) NULL,
    reused_step_id uuid NULL,
    error_code varchar(100) NULL,
    error_message_safe varchar(500) NULL,
    created_at timestamptz NOT NULL,
    started_at timestamptz NULL,
    completed_at timestamptz NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT agent_steps_pk PRIMARY KEY (id),
    CONSTRAINT agent_steps_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT agent_steps_user_run_id_uk UNIQUE (user_id, agent_run_id, id),
    CONSTRAINT agent_steps_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT agent_steps_run_owner_fk FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id) ON DELETE CASCADE,
    CONSTRAINT agent_steps_reused_owner_fk FOREIGN KEY (user_id, reused_step_id)
        REFERENCES agent_steps(user_id, id),
    CONSTRAINT agent_steps_model_policy_fk FOREIGN KEY (model_policy_version)
        REFERENCES ai_model_policies(version),
    CONSTRAINT agent_steps_status_ck CHECK (status IN (
        'PENDING', 'RUNNING', 'WAITING_USER', 'SUCCEEDED', 'FAILED',
        'SKIPPED', 'REUSED', 'CANCELLED', 'INTERRUPTED'
    )),
    CONSTRAINT agent_steps_order_ck CHECK (step_order >= 1),
    CONSTRAINT agent_steps_attempt_ck CHECK (attempt BETWEEN 1 AND max_attempts AND max_attempts BETWEEN 1 AND 3),
    CONSTRAINT agent_steps_input_hash_ck CHECK (input_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT agent_steps_output_hash_ck CHECK (output_hash IS NULL OR output_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT agent_steps_input_refs_ck CHECK (jsonb_typeof(input_refs) = 'object'),
    CONSTRAINT agent_steps_output_json_ck CHECK (output_json IS NULL OR jsonb_typeof(output_json) = 'object'),
    CONSTRAINT agent_steps_quality_ck CHECK (
        requested_quality_mode IS NULL OR requested_quality_mode IN ('ECONOMY', 'BALANCED', 'HIGH_QUALITY')
    ),
    CONSTRAINT agent_steps_model_tier_ck CHECK (
        model_tier_used IS NULL OR model_tier_used IN ('LOW_COST', 'BALANCED', 'HIGH_QUALITY')
    ),
    CONSTRAINT agent_steps_reuse_shape_ck CHECK (
        (status = 'REUSED' AND reused_step_id IS NOT NULL AND output_hash IS NOT NULL AND output_json IS NOT NULL)
        OR (status <> 'REUSED' AND reused_step_id IS NULL)
    ),
    CONSTRAINT agent_steps_safe_error_pair_ck CHECK (
        (error_code IS NULL AND error_message_safe IS NULL)
        OR (error_code IS NOT NULL AND error_message_safe IS NOT NULL)
    ),
    CONSTRAINT agent_steps_terminal_timestamp_ck CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'SKIPPED', 'REUSED', 'CANCELLED', 'INTERRUPTED') AND completed_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'FAILED', 'SKIPPED', 'REUSED', 'CANCELLED', 'INTERRUPTED') AND completed_at IS NULL)
    ),
    CONSTRAINT agent_steps_time_order_ck CHECK (
        updated_at >= created_at
        AND (started_at IS NULL OR started_at >= created_at)
        AND (completed_at IS NULL OR completed_at >= created_at)
    ),
    CONSTRAINT agent_steps_attempt_scope_uk UNIQUE NULLS NOT DISTINCT (
        user_id, agent_run_id, step_key, scope_key, attempt
    )
);

CREATE INDEX agent_steps_run_order_ix
    ON agent_steps (user_id, agent_run_id, step_order, scope_key, attempt);
CREATE INDEX agent_steps_reuse_ix
    ON agent_steps (user_id, step_key, scope_key, input_hash, requested_quality_mode)
    WHERE status IN ('SUCCEEDED', 'REUSED');

CREATE TABLE ai_budget_ledgers (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    budget_date date NOT NULL,
    budget_zone varchar(50) NOT NULL,
    spent_usd numeric(12,6) NOT NULL DEFAULT 0,
    reserved_usd numeric(12,6) NOT NULL DEFAULT 0,
    policy_version bigint NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT ai_budget_ledgers_pk PRIMARY KEY (id),
    CONSTRAINT ai_budget_ledgers_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT ai_budget_ledgers_user_date_zone_uk UNIQUE (user_id, budget_date, budget_zone),
    CONSTRAINT ai_budget_ledgers_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ai_budget_ledgers_policy_fk FOREIGN KEY (policy_version)
        REFERENCES ai_budget_policy_versions(version),
    CONSTRAINT ai_budget_ledgers_zone_ck CHECK (budget_zone = 'Asia/Seoul'),
    CONSTRAINT ai_budget_ledgers_cost_ck CHECK (spent_usd >= 0 AND reserved_usd >= 0),
    CONSTRAINT ai_budget_ledgers_version_ck CHECK (version >= 0)
);

CREATE TABLE ai_budget_reservations (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    ledger_id uuid NOT NULL,
    operation_type varchar(80) NOT NULL,
    agent_run_id uuid NOT NULL,
    reserved_usd numeric(12,6) NOT NULL,
    settled_usd numeric(12,6) NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    budget_policy_version bigint NOT NULL,
    price_version bigint NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    settled_at timestamptz NULL,
    released_at timestamptz NULL,
    CONSTRAINT ai_budget_reservations_pk PRIMARY KEY (id),
    CONSTRAINT ai_budget_reservations_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT ai_budget_reservations_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ai_budget_reservations_ledger_owner_fk FOREIGN KEY (user_id, ledger_id)
        REFERENCES ai_budget_ledgers(user_id, id),
    CONSTRAINT ai_budget_reservations_run_owner_fk FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id) ON DELETE CASCADE,
    CONSTRAINT ai_budget_reservations_budget_policy_fk FOREIGN KEY (budget_policy_version)
        REFERENCES ai_budget_policy_versions(version),
    CONSTRAINT ai_budget_reservations_price_version_fk FOREIGN KEY (price_version)
        REFERENCES ai_price_versions(version),
    CONSTRAINT ai_budget_reservations_status_ck CHECK (status IN ('RESERVED', 'SETTLED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ai_budget_reservations_cost_ck CHECK (
        reserved_usd >= 0 AND settled_usd >= 0 AND settled_usd <= reserved_usd
    ),
    CONSTRAINT ai_budget_reservations_paid_price_ck CHECK (reserved_usd = 0 OR price_version IS NOT NULL),
    CONSTRAINT ai_budget_reservations_terminal_time_ck CHECK (
        (status = 'SETTLED' AND settled_at IS NOT NULL AND released_at IS NULL)
        OR (status IN ('RELEASED', 'EXPIRED') AND released_at IS NOT NULL)
        OR (status = 'RESERVED' AND settled_at IS NULL AND released_at IS NULL)
    ),
    CONSTRAINT ai_budget_reservations_expiry_ck CHECK (expires_at > created_at)
);

CREATE INDEX ai_budget_reservations_active_ix
    ON ai_budget_reservations (user_id, status, expires_at);

CREATE UNIQUE INDEX ai_budget_reservations_active_run_uk
    ON ai_budget_reservations (user_id, agent_run_id)
    WHERE status = 'RESERVED';

CREATE TABLE ai_usage_records (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    agent_run_id uuid NOT NULL,
    agent_step_id uuid NULL,
    operation_type varchar(80) NOT NULL,
    usage_type varchar(20) NOT NULL,
    provider_key varchar(100) NOT NULL,
    product_key varchar(150) NOT NULL,
    model_tier varchar(30) NULL,
    input_units bigint NOT NULL DEFAULT 0,
    cached_input_units bigint NOT NULL DEFAULT 0,
    output_units bigint NOT NULL DEFAULT 0,
    embedding_units bigint NOT NULL DEFAULT 0,
    search_units bigint NOT NULL DEFAULT 0,
    price_version bigint NULL,
    price_item_id uuid NULL,
    cost_usd numeric(12,6) NOT NULL,
    duration_ms bigint NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ai_usage_records_pk PRIMARY KEY (id),
    CONSTRAINT ai_usage_records_user_id_id_uk UNIQUE (user_id, id),
    CONSTRAINT ai_usage_records_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ai_usage_records_run_owner_fk FOREIGN KEY (user_id, agent_run_id)
        REFERENCES agent_runs(user_id, id) ON DELETE CASCADE,
    CONSTRAINT ai_usage_records_step_run_owner_fk FOREIGN KEY (user_id, agent_run_id, agent_step_id)
        REFERENCES agent_steps(user_id, agent_run_id, id),
    CONSTRAINT ai_usage_records_price_item_fk FOREIGN KEY (price_version, price_item_id)
        REFERENCES ai_price_items(price_version, id),
    CONSTRAINT ai_usage_records_usage_type_ck CHECK (usage_type IN ('CHAT', 'EMBEDDING', 'SEARCH')),
    CONSTRAINT ai_usage_records_model_tier_ck CHECK (
        model_tier IS NULL OR model_tier IN ('LOW_COST', 'BALANCED', 'HIGH_QUALITY')
    ),
    CONSTRAINT ai_usage_records_units_ck CHECK (
        input_units >= 0 AND cached_input_units >= 0 AND output_units >= 0
        AND embedding_units >= 0 AND search_units >= 0
    ),
    CONSTRAINT ai_usage_records_cost_ck CHECK (cost_usd >= 0),
    CONSTRAINT ai_usage_records_duration_ck CHECK (duration_ms >= 0),
    CONSTRAINT ai_usage_records_paid_price_ck CHECK (
        cost_usd = 0 OR (price_version IS NOT NULL AND price_item_id IS NOT NULL)
    ),
    CONSTRAINT ai_usage_records_price_pair_ck CHECK (
        (price_version IS NULL AND price_item_id IS NULL)
        OR (price_version IS NOT NULL AND price_item_id IS NOT NULL)
    )
);

CREATE INDEX ai_usage_records_run_ix
    ON ai_usage_records (user_id, agent_run_id, created_at);

ALTER TABLE idempotency_records
    ADD CONSTRAINT idempotency_records_resource_pair_ck CHECK (
        (resource_type IS NULL AND resource_id IS NULL)
        OR (resource_type IS NOT NULL AND resource_id IS NOT NULL)
    ),
    ADD CONSTRAINT idempotency_records_agent_run_owner_fk
        FOREIGN KEY (user_id, agent_run_id) REFERENCES agent_runs(user_id, id);
