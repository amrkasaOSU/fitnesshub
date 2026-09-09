CREATE TABLE exercises (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    muscle_group        VARCHAR(20) NOT NULL,
    equipment           VARCHAR(20) NOT NULL,
    movement_pattern    VARCHAR(20) NOT NULL,
    instructions        TEXT,
    video_url           TEXT,
    image_url           TEXT,
    is_system_exercise  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_exercises_muscle_group ON exercises (muscle_group);
CREATE INDEX idx_exercises_equipment ON exercises (equipment);
CREATE INDEX idx_exercises_name ON exercises (LOWER(name));

CREATE TABLE programs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id             UUID NOT NULL REFERENCES users(id),
    name                 VARCHAR(200) NOT NULL,
    description          TEXT,
    duration_weeks       INTEGER NOT NULL,
    goal                 VARCHAR(30) NOT NULL,
    version              INTEGER NOT NULL DEFAULT 1,
    previous_version_id  UUID REFERENCES programs(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_programs_coach_id ON programs (coach_id);

CREATE TABLE program_days (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_id   UUID NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    day_number   INTEGER NOT NULL,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_program_days_program_id ON program_days (program_id);

CREATE TABLE program_exercises (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    program_day_id        UUID NOT NULL REFERENCES program_days(id) ON DELETE CASCADE,
    exercise_id           UUID NOT NULL REFERENCES exercises(id),
    order_index           INTEGER NOT NULL,
    sets                  INTEGER NOT NULL CHECK (sets > 0),
    target_reps           INTEGER NOT NULL CHECK (target_reps > 0),
    target_weight         NUMERIC(7,2) CHECK (target_weight IS NULL OR target_weight >= 0),
    target_rpe            NUMERIC(3,1) CHECK (target_rpe IS NULL OR (target_rpe >= 0 AND target_rpe <= 10)),
    rest_seconds          INTEGER,
    tempo                 VARCHAR(20),
    notes                 TEXT,
    progression_strategy  VARCHAR(30) NOT NULL DEFAULT 'DOUBLE_PROGRESSION',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_program_exercises_day_id ON program_exercises (program_day_id);
CREATE INDEX idx_program_exercises_exercise_id ON program_exercises (exercise_id);

CREATE TABLE client_programs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id   UUID NOT NULL REFERENCES users(id),
    program_id  UUID NOT NULL REFERENCES programs(id),
    start_date  DATE NOT NULL,
    end_date    DATE,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_client_programs_status CHECK (status IN ('ACTIVE','COMPLETED','PAUSED','CANCELLED'))
);
CREATE INDEX idx_client_programs_client_id ON client_programs (client_id);
CREATE INDEX idx_client_programs_program_id ON client_programs (program_id);
