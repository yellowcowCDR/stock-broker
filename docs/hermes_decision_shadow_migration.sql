-- Hermes decision-write and SHADOW real-quote sample migration.
-- PostgreSQL; safe to rerun. All timestamps are TIMESTAMPTZ and are stored as UTC instants.

ALTER TABLE IF EXISTS trading_feature_snapshot
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);

ALTER TABLE IF EXISTS trading_decision
    ADD COLUMN IF NOT EXISTS decision_mode VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160);

UPDATE trading_decision
SET decision_mode = 'ACTIVE'
WHERE decision_mode IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_feature_idempotency
    ON trading_feature_snapshot (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_decision_idempotency
    ON trading_decision (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_decision_feature_strategy_mode
    ON trading_decision (feature_id, strategy_version, decision_mode);

CREATE UNIQUE INDEX IF NOT EXISTS ux_trading_logs_decision_id
    ON trading_logs (decision_id)
    WHERE decision_id IS NOT NULL;

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
