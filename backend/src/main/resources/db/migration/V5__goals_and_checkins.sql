CREATE TABLE goals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id       UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(30) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    target_value    NUMERIC(10,2) NOT NULL,
    current_value   NUMERIC(10,2) NOT NULL DEFAULT 0,
    unit            VARCHAR(30) NOT NULL,
    target_date     DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_goals_type CHECK (type IN ('WEIGHT','STRENGTH','STEPS','BODY_FAT','WORKOUT_ADHERENCE','CUSTOM')),
    CONSTRAINT chk_goals_status CHECK (status IN ('ACTIVE','ACHIEVED','ABANDONED'))
);
CREATE INDEX idx_goals_client_id ON goals (client_id);

CREATE TABLE check_ins (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID NOT NULL REFERENCES users(id),
    week_start_date       DATE NOT NULL,
    weight                NUMERIC(6,2),
    energy_score          INTEGER NOT NULL CHECK (energy_score BETWEEN 1 AND 5),
    sleep_score           INTEGER NOT NULL CHECK (sleep_score BETWEEN 1 AND 5),
    stress_score          INTEGER NOT NULL CHECK (stress_score BETWEEN 1 AND 5),
    hunger_score          INTEGER NOT NULL CHECK (hunger_score BETWEEN 1 AND 5),
    workout_adherence     INTEGER NOT NULL CHECK (workout_adherence BETWEEN 1 AND 5),
    nutrition_adherence   INTEGER NOT NULL CHECK (nutrition_adherence BETWEEN 1 AND 5),
    notes                 TEXT,
    submitted_at          TIMESTAMPTZ NOT NULL,
    coach_reviewed_at     TIMESTAMPTZ,
    coach_response        TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_check_ins UNIQUE (client_id, week_start_date)
);
CREATE INDEX idx_check_ins_client_id ON check_ins (client_id);
