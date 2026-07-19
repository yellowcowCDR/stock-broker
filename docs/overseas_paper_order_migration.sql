-- Overseas Paper order audit metadata.
-- Safe to run repeatedly. No legacy exchange is guessed or backfilled.
BEGIN;

ALTER TABLE IF EXISTS trading_logs
    ADD COLUMN IF NOT EXISTS market_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS exchange_code VARCHAR(10);

-- A database old enough to lack market_type can only contain legacy domestic orders.
UPDATE trading_logs
SET market_type = 'DOMESTIC'
WHERE market_type IS NULL;

ALTER TABLE IF EXISTS trading_logs
    ALTER COLUMN market_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_trading_logs_market_exchange_stock
    ON trading_logs (market_type, exchange_code, stock_code);

COMMIT;
