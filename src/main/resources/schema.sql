-- This file is provided for reference only. 
-- The application relies on spring.jpa.hibernate.ddl-auto=update to manage schema changes in local environment.

CREATE TABLE IF NOT EXISTS trading_feature_snapshot (
    feature_id VARCHAR(36) PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    technical_features JSONB,
    news_features JSONB,
    risk_features JSONB,
    snapshot_at TIMESTAMP NOT NULL
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
    decided_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS trading_reflection (
    reflection_id VARCHAR(36) PRIMARY KEY,
    trading_date DATE NOT NULL,
    strategy_version VARCHAR(50) NOT NULL,
    daily_return_rate NUMERIC(20, 4),
    market_return_rate NUMERIC(20, 4),
    reviews JSONB,
    overall_feedback VARCHAR(2000),
    improvement_plan VARCHAR(2000),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_skill_performance (
    skill_version VARCHAR(50) PRIMARY KEY,
    win_rate NUMERIC(20, 4),
    profit_factor NUMERIC(20, 4),
    max_drawdown NUMERIC(20, 4),
    total_return_rate NUMERIC(20, 4),
    trade_count INTEGER NOT NULL,
    evaluated_at TIMESTAMP NOT NULL
);
