-- Apply during a maintenance window after a database backup.
-- This migration preserves the currently active legacy version as ACTIVE and
-- marks older inactive versions ROLLED_BACK. It does not activate any candidate.

BEGIN;

ALTER TABLE agent_skills
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS parent_version INTEGER,
    ADD COLUMN IF NOT EXISTS shadow_evaluation JSONB,
    ADD COLUMN IF NOT EXISTS status_reason VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS status_changed_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS row_version BIGINT DEFAULT 0;

UPDATE agent_skills
SET lifecycle_status = CASE WHEN is_active THEN 'ACTIVE' ELSE 'ROLLED_BACK' END,
    status_reason = COALESCE(status_reason, 'Migrated from legacy is_active state'),
    status_changed_by = COALESCE(status_changed_by, 'LEGACY_MIGRATION'),
    status_changed_at = COALESCE(status_changed_at, created_at),
    row_version = COALESCE(row_version, 0)
WHERE lifecycle_status IS NULL
   OR status_reason IS NULL
   OR status_changed_by IS NULL
   OR status_changed_at IS NULL
   OR row_version IS NULL;

ALTER TABLE agent_skills
    ALTER COLUMN lifecycle_status SET NOT NULL,
    ALTER COLUMN row_version SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_agent_skills_single_active
    ON agent_skills (is_active)
    WHERE is_active = TRUE;

COMMIT;
