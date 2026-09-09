CREATE TABLE coach_notes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id    UUID NOT NULL REFERENCES users(id),
    client_id   UUID NOT NULL REFERENCES users(id),
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_coach_notes_coach_client ON coach_notes (coach_id, client_id);

CREATE TABLE workout_feedback (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workout_session_id   UUID NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    coach_id             UUID NOT NULL REFERENCES users(id),
    content              TEXT NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workout_feedback_session_id ON workout_feedback (workout_session_id);
