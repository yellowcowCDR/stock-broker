-- Apply during a maintenance window after a database backup.
-- Legacy reflections are deliberately marked incomplete. Existing costs and
-- decision links cannot be reconstructed safely from assumptions.
-- Run this entire file in one database session. Do not execute an UPDATE block
-- by itself: the preceding ADD COLUMN is part of the same transaction. If any
-- statement fails, run ROLLBACK in that session and rerun the entire file after
-- resolving the reported cause; PostgreSQL rolls back earlier ALTER statements too.

BEGIN;

ALTER TABLE trading_logs
    ADD COLUMN IF NOT EXISTS decision_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS feature_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS strategy_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS transaction_cost NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS cost_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS cost_source VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cost_data_complete BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS slippage_amount NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMPTZ;

UPDATE trading_logs
SET cost_data_complete = FALSE
WHERE cost_data_complete IS NULL;

ALTER TABLE trading_logs
    ALTER COLUMN cost_data_complete SET DEFAULT FALSE,
    ALTER COLUMN cost_data_complete SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trading_logs_decision_id
    ON trading_logs (decision_id);

ALTER TABLE daily_summaries
    ADD COLUMN IF NOT EXISTS market_type VARCHAR(20);

UPDATE daily_summaries
SET market_type = 'DOMESTIC'
WHERE market_type IS NULL;

ALTER TABLE daily_summaries
    ALTER COLUMN market_type SET NOT NULL;

DO $$
DECLARE
    old_constraint TEXT;
BEGIN
    FOR old_constraint IN
        SELECT constraint_name
        FROM information_schema.table_constraints
        WHERE table_schema = current_schema()
          AND table_name = 'daily_summaries'
          AND constraint_type = 'UNIQUE'
    LOOP
        IF pg_get_constraintdef(
                (SELECT oid FROM pg_constraint WHERE conname = old_constraint
                 AND conrelid = 'daily_summaries'::regclass LIMIT 1)) = 'UNIQUE (trade_date)'
        THEN
            EXECUTE format('ALTER TABLE daily_summaries DROP CONSTRAINT %I', old_constraint);
        END IF;
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_daily_summaries_market_date
    ON daily_summaries (market_type, trade_date);

ALTER TABLE trading_reflection
    ADD COLUMN IF NOT EXISTS market_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS market_zone_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS total_transaction_cost NUMERIC(20, 4),
    ADD COLUMN IF NOT EXISTS total_slippage_amount NUMERIC(20, 4),
    ADD COLUMN IF NOT EXISTS decision_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS hold_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS block_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS data_complete BOOLEAN DEFAULT FALSE;

UPDATE trading_reflection
SET market_type = COALESCE(market_type, 'DOMESTIC'),
    market_zone_id = COALESCE(market_zone_id, 'Asia/Seoul'),
    decision_count = COALESCE(decision_count, 0),
    hold_count = COALESCE(hold_count, 0),
    block_count = COALESCE(block_count, 0),
    data_complete = FALSE;

ALTER TABLE trading_reflection
    ALTER COLUMN market_type SET NOT NULL,
    ALTER COLUMN market_zone_id SET NOT NULL,
    ALTER COLUMN decision_count SET NOT NULL,
    ALTER COLUMN hold_count SET NOT NULL,
    ALTER COLUMN block_count SET NOT NULL,
    ALTER COLUMN data_complete SET DEFAULT FALSE,
    ALTER COLUMN data_complete SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_reflection_identity
    ON trading_reflection (trading_date, market_type, strategy_version);

CREATE TABLE IF NOT EXISTS cron_heartbeat (
    cron_name VARCHAR(100) PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    phase VARCHAR(20) NOT NULL,
    expected_interval_seconds BIGINT NOT NULL,
    last_started_at TIMESTAMPTZ,
    last_completed_at TIMESTAMPTZ,
    expected_next_at TIMESTAMPTZ NOT NULL,
    message VARCHAR(1000),
    updated_at TIMESTAMPTZ NOT NULL,
    row_version BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS broker_runtime_state (
    state_key VARCHAR(100) PRIMARY KEY,
    state_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

COMMIT;
