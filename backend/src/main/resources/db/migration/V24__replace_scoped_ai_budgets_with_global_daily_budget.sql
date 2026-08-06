-- One operational AI spend ceiling shared by every workflow and user.
-- Product entitlements (plans/credits) intentionally remain outside this ledger.

UPDATE ai_budget_policy_versions
SET active = false
WHERE active;

ALTER TABLE ai_budget_policy_versions
    ADD COLUMN daily_budget_usd numeric(12,6) NOT NULL DEFAULT 10.000000;

INSERT INTO ai_budget_policy_versions (
    id, version, user_default_daily_budget_usd, system_max_daily_budget_usd,
    async_run_max_cost_usd, mock_turn_max_cost_usd, mock_session_max_cost_usd,
    reset_zone, active, created_at, daily_budget_usd
) VALUES (
    '00000000-0000-0000-0000-000000000302', 2,
    0, 0, 0, 0, 0,
    'Asia/Seoul', true, CURRENT_TIMESTAMP, 10.000000
);

ALTER TABLE ai_budget_policy_versions
    DROP CONSTRAINT ai_budget_policy_versions_order_ck,
    DROP CONSTRAINT ai_budget_policy_versions_user_default_ck,
    DROP CONSTRAINT ai_budget_policy_versions_system_max_ck,
    DROP CONSTRAINT ai_budget_policy_versions_async_run_ck,
    DROP CONSTRAINT ai_budget_policy_versions_mock_turn_ck,
    DROP CONSTRAINT ai_budget_policy_versions_mock_session_ck,
    DROP COLUMN user_default_daily_budget_usd,
    DROP COLUMN system_max_daily_budget_usd,
    DROP COLUMN async_run_max_cost_usd,
    DROP COLUMN mock_turn_max_cost_usd,
    DROP COLUMN mock_session_max_cost_usd,
    ALTER COLUMN daily_budget_usd DROP DEFAULT,
    ADD CONSTRAINT ai_budget_policy_versions_daily_budget_ck CHECK (daily_budget_usd > 0);

ALTER TABLE user_ai_preferences
    DROP CONSTRAINT user_ai_preferences_budget_policy_fk,
    DROP CONSTRAINT user_ai_preferences_daily_budget_ck,
    DROP COLUMN budget_policy_version,
    DROP COLUMN daily_budget_usd;

ALTER TABLE ai_budget_reservations
    DROP CONSTRAINT ai_budget_reservations_ledger_owner_fk;

ALTER TABLE ai_budget_ledgers
    DROP CONSTRAINT ai_budget_ledgers_user_id_fk,
    DROP CONSTRAINT ai_budget_ledgers_user_id_id_uk,
    DROP CONSTRAINT ai_budget_ledgers_user_date_zone_uk,
    ALTER COLUMN user_id DROP NOT NULL;

-- Consolidate legacy per-user ledgers before removing their ownership dimension.
-- Existing reservations are repointed so in-flight runs remain settleable after deployment.
INSERT INTO ai_budget_ledgers (
    id, user_id, budget_date, budget_zone, spent_usd, reserved_usd,
    policy_version, version, created_at, updated_at
)
SELECT
    gen_random_uuid(), NULL, ledger.budget_date, ledger.budget_zone,
    SUM(ledger.spent_usd), SUM(ledger.reserved_usd), policy.version, 0,
    MIN(ledger.created_at), MAX(ledger.updated_at)
FROM ai_budget_ledgers ledger
JOIN ai_budget_policy_versions policy ON policy.active
WHERE ledger.user_id IS NOT NULL
GROUP BY ledger.budget_date, ledger.budget_zone, policy.version;

UPDATE ai_budget_reservations reservation
SET ledger_id = global_ledger.id
FROM ai_budget_ledgers legacy_ledger
JOIN ai_budget_ledgers global_ledger
  ON global_ledger.user_id IS NULL
 AND global_ledger.budget_date = legacy_ledger.budget_date
 AND global_ledger.budget_zone = legacy_ledger.budget_zone
WHERE reservation.ledger_id = legacy_ledger.id
  AND legacy_ledger.user_id IS NOT NULL;

DELETE FROM ai_budget_ledgers
WHERE user_id IS NOT NULL;

ALTER TABLE ai_budget_ledgers
    DROP COLUMN user_id,
    ADD CONSTRAINT ai_budget_ledgers_date_zone_uk UNIQUE (budget_date, budget_zone);

ALTER TABLE ai_budget_reservations
    ADD CONSTRAINT ai_budget_reservations_ledger_fk
        FOREIGN KEY (ledger_id) REFERENCES ai_budget_ledgers(id);
