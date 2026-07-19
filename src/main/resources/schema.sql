-- This file is provided for reference only. 
-- The application relies on spring.jpa.hibernate.ddl-auto=update to manage schema changes in local environment.

CREATE TABLE IF NOT EXISTS trading_feature_snapshot (
    feature_id VARCHAR(36) PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    market_type VARCHAR(20),
    technical_features JSONB,
    news_features JSONB,
    risk_features JSONB,
    snapshot_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(160)
);

CREATE TABLE IF NOT EXISTS trading_decision (
    decision_id VARCHAR(36) PRIMARY KEY,
    feature_id VARCHAR(36) NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    decision_type VARCHAR(20) NOT NULL,
    strategy_version VARCHAR(50) NOT NULL,
    reason VARCHAR(1000),
    recommended_price NUMERIC(20, 4),
    recommended_quantity NUMERIC(20, 4),
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decision_mode VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    idempotency_key VARCHAR(160)
);

ALTER TABLE IF EXISTS trading_feature_snapshot
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);

ALTER TABLE IF EXISTS trading_decision
    ADD COLUMN IF NOT EXISTS decision_mode VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_feature_idempotency
    ON trading_feature_snapshot (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_decision_idempotency
    ON trading_decision (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_decision_feature_strategy_mode
    ON trading_decision (feature_id, strategy_version, decision_mode);

CREATE TABLE IF NOT EXISTS shadow_performance_sample (
    sample_id VARCHAR(36) PRIMARY KEY,
    decision_id VARCHAR(36) NOT NULL,
    feature_id VARCHAR(36) NOT NULL,
    strategy_version VARCHAR(50) NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    exchange_code VARCHAR(10),
    decision_type VARCHAR(20) NOT NULL,
    reference_price NUMERIC(20, 4) NOT NULL,
    observed_price NUMERIC(20, 4),
    raw_return_rate NUMERIC(20, 6),
    action_return_rate NUMERIC(20, 6),
    trading_date DATE NOT NULL,
    sample_status VARCHAR(20) NOT NULL,
    data_source VARCHAR(100) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_shadow_sample_decision
    ON shadow_performance_sample (decision_id);

CREATE INDEX IF NOT EXISTS idx_shadow_sample_strategy_status
    ON shadow_performance_sample (strategy_version, sample_status, started_at);

CREATE TABLE IF NOT EXISTS trading_reflection (
    reflection_id VARCHAR(36) PRIMARY KEY,
    trading_date DATE NOT NULL,
    market_type VARCHAR(20) NOT NULL,
    market_zone_id VARCHAR(50) NOT NULL,
    strategy_version VARCHAR(50) NOT NULL,
    daily_return_rate NUMERIC(20, 4),
    market_return_rate NUMERIC(20, 4),
    total_transaction_cost NUMERIC(20, 4),
    total_slippage_amount NUMERIC(20, 4),
    decision_count INTEGER NOT NULL,
    hold_count INTEGER NOT NULL,
    block_count INTEGER NOT NULL,
    data_complete BOOLEAN NOT NULL,
    reviews JSONB,
    overall_feedback VARCHAR(2000),
    improvement_plan VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_skill_performance (
    skill_version VARCHAR(50) PRIMARY KEY,
    win_rate NUMERIC(20, 4),
    profit_factor NUMERIC(20, 4),
    max_drawdown NUMERIC(20, 4),
    total_return_rate NUMERIC(20, 4),
    trade_count INTEGER NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS market_context (
    context_id VARCHAR(36) PRIMARY KEY,
    market_type VARCHAR(20) NOT NULL,
    entry_policy VARCHAR(30) NOT NULL,
    risk_multiplier NUMERIC(10, 6) NOT NULL,
    overview_snapshot JSONB NOT NULL,
    rationale JSONB NOT NULL,
    overview_data_source VARCHAR(200) NOT NULL,
    overview_fetched_at TIMESTAMP WITH TIME ZONE NOT NULL,
    analyzed_by VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(100) NOT NULL,
    analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_context_market_analyzed
    ON market_context (market_type, analyzed_at DESC);

ALTER TABLE IF EXISTS trading_logs
    ADD COLUMN IF NOT EXISTS market_context_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS exchange_code VARCHAR(10),
    ADD COLUMN IF NOT EXISTS decision_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS feature_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS strategy_version VARCHAR(50),
    ADD COLUMN IF NOT EXISTS transaction_cost NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS cost_currency VARCHAR(3),
    ADD COLUMN IF NOT EXISTS cost_source VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cost_data_complete BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS slippage_amount NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS reconciled_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_trading_logs_market_context_id
    ON trading_logs (market_context_id);

CREATE INDEX IF NOT EXISTS idx_trading_logs_decision_id
    ON trading_logs (decision_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_logs_decision_id
    ON trading_logs (decision_id)
    WHERE decision_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trading_logs_market_exchange_stock
    ON trading_logs (market_type, exchange_code, stock_code);

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_reflection_identity
    ON trading_reflection (trading_date, market_type, strategy_version);

CREATE TABLE IF NOT EXISTS cron_heartbeat (
    cron_name VARCHAR(100) PRIMARY KEY,
    execution_id VARCHAR(100) NOT NULL,
    phase VARCHAR(20) NOT NULL,
    expected_interval_seconds BIGINT NOT NULL,
    last_started_at TIMESTAMP WITH TIME ZONE,
    last_completed_at TIMESTAMP WITH TIME ZONE,
    expected_next_at TIMESTAMP WITH TIME ZONE NOT NULL,
    message VARCHAR(1000),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    row_version BIGINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS broker_runtime_state (
    state_key VARCHAR(100) PRIMARY KEY,
    state_value VARCHAR(500) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE IF EXISTS agent_skills
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS parent_version INTEGER,
    ADD COLUMN IF NOT EXISTS shadow_evaluation JSONB,
    ADD COLUMN IF NOT EXISTS status_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS status_changed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS row_version BIGINT DEFAULT 0;

UPDATE agent_skills
SET lifecycle_status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'ROLLED_BACK' END,
    status_changed_at = COALESCE(status_changed_at, created_at),
    status_changed_by = COALESCE(status_changed_by, 'LEGACY_MIGRATION'),
    row_version = COALESCE(row_version, 0)
WHERE lifecycle_status IS NULL
   OR status_changed_at IS NULL
   OR status_changed_by IS NULL
   OR row_version IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_agent_skills_single_active
    ON agent_skills (is_active)
    WHERE is_active = TRUE;
