-- DO NOT RUN BLINDLY. First confirm the zone represented by existing timestamp values.
-- This default assumes they were written as UTC. If the legacy JVM/server used
-- Asia/Seoul local time, replace every AT TIME ZONE 'UTC' below with
-- AT TIME ZONE 'Asia/Seoul'. Run only during a maintenance window after a backup.

ALTER TABLE trading_logs
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN submitted_at TYPE TIMESTAMPTZ USING submitted_at AT TIME ZONE 'UTC';

ALTER TABLE trading_feature_snapshot
    ALTER COLUMN snapshot_at TYPE TIMESTAMPTZ USING snapshot_at AT TIME ZONE 'UTC';

ALTER TABLE trading_decision
    ALTER COLUMN decided_at TYPE TIMESTAMPTZ USING decided_at AT TIME ZONE 'UTC';

ALTER TABLE trading_reflection
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE agent_skill_performance
    ALTER COLUMN evaluated_at TYPE TIMESTAMPTZ USING evaluated_at AT TIME ZONE 'UTC';

ALTER TABLE agent_skills
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE daily_summaries
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE api_call_log
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
