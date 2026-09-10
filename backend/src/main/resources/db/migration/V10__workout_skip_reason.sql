-- Skipping a workout is a deliberate, explained act: the reason is the whole
-- point of the feature (it's what gets sent to the coach), so a SKIPPED row
-- without one is not a valid state.
ALTER TABLE workout_sessions ADD COLUMN skip_reason TEXT;

ALTER TABLE workout_sessions ADD CONSTRAINT chk_workout_sessions_skip_reason
    CHECK (status <> 'SKIPPED' OR skip_reason IS NOT NULL);
