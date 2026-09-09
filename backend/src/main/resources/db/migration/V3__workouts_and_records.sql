CREATE TABLE workout_sessions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id          UUID NOT NULL REFERENCES users(id),
    program_day_id     UUID REFERENCES program_days(id),
    started_at         TIMESTAMPTZ NOT NULL,
    completed_at       TIMESTAMPTZ,
    duration_seconds   INTEGER,
    notes              TEXT,
    overall_rpe        NUMERIC(3,1),
    energy_level       INTEGER,
    status             VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_workout_sessions_status CHECK (status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED','SKIPPED')),
    CONSTRAINT chk_workout_sessions_energy CHECK (energy_level IS NULL OR (energy_level BETWEEN 1 AND 5))
);
CREATE INDEX idx_workout_sessions_client_id ON workout_sessions (client_id);
CREATE INDEX idx_workout_sessions_started_at ON workout_sessions (started_at);

CREATE TABLE workout_exercises (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workout_session_id    UUID NOT NULL REFERENCES workout_sessions(id) ON DELETE CASCADE,
    exercise_id           UUID NOT NULL REFERENCES exercises(id),
    order_index           INTEGER NOT NULL,
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workout_exercises_session_id ON workout_exercises (workout_session_id);
CREATE INDEX idx_workout_exercises_exercise_id ON workout_exercises (exercise_id);

CREATE TABLE workout_sets (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workout_exercise_id    UUID NOT NULL REFERENCES workout_exercises(id) ON DELETE CASCADE,
    set_number             INTEGER NOT NULL,
    reps                   INTEGER NOT NULL CHECK (reps >= 0),
    weight                 NUMERIC(7,2) NOT NULL CHECK (weight >= 0),
    rpe                    NUMERIC(3,1) CHECK (rpe IS NULL OR (rpe >= 0 AND rpe <= 10)),
    rir                    NUMERIC(3,1) CHECK (rir IS NULL OR (rir >= 0 AND rir <= 10)),
    is_warmup              BOOLEAN NOT NULL DEFAULT FALSE,
    is_failure             BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at           TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_workout_sets_workout_exercise_id ON workout_sets (workout_exercise_id);

CREATE TABLE personal_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID NOT NULL REFERENCES users(id),
    exercise_id     UUID NOT NULL REFERENCES exercises(id),
    record_type     VARCHAR(20) NOT NULL,
    value           NUMERIC(10,2) NOT NULL,
    workout_set_id  UUID REFERENCES workout_sets(id),
    achieved_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_personal_records UNIQUE (client_id, exercise_id, record_type),
    CONSTRAINT chk_personal_records_type CHECK (record_type IN ('MAX_WEIGHT','MAX_REPS','ESTIMATED_1RM','MAX_VOLUME'))
);
CREATE INDEX idx_personal_records_client_id ON personal_records (client_id);
CREATE INDEX idx_personal_records_achieved_at ON personal_records (achieved_at);
