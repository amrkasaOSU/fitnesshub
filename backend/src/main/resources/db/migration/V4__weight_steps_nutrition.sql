CREATE TABLE weight_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id    UUID NOT NULL REFERENCES users(id),
    weight       NUMERIC(6,2) NOT NULL CHECK (weight > 0),
    recorded_at  TIMESTAMPTZ NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_weight_entries_client_id ON weight_entries (client_id);
CREATE INDEX idx_weight_entries_recorded_at ON weight_entries (recorded_at);

CREATE TABLE step_entries (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id    UUID NOT NULL REFERENCES users(id),
    steps        INTEGER NOT NULL CHECK (steps >= 0),
    date         DATE NOT NULL,
    source       VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_step_entries UNIQUE (client_id, date)
);
CREATE INDEX idx_step_entries_client_id ON step_entries (client_id);
CREATE INDEX idx_step_entries_date ON step_entries (date);

CREATE TABLE nutrition_entries (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id              UUID NOT NULL REFERENCES users(id),
    date                   DATE NOT NULL,
    calories               NUMERIC(7,2) NOT NULL CHECK (calories >= 0),
    protein_grams          NUMERIC(6,2) NOT NULL CHECK (protein_grams >= 0),
    carbohydrates_grams    NUMERIC(6,2) CHECK (carbohydrates_grams IS NULL OR carbohydrates_grams >= 0),
    fat_grams              NUMERIC(6,2) CHECK (fat_grams IS NULL OR fat_grams >= 0),
    fiber_grams            NUMERIC(6,2) CHECK (fiber_grams IS NULL OR fiber_grams >= 0),
    water_ml               INTEGER CHECK (water_ml IS NULL OR water_ml >= 0),
    notes                  TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_nutrition_entries UNIQUE (client_id, date)
);
CREATE INDEX idx_nutrition_entries_client_id ON nutrition_entries (client_id);
CREATE INDEX idx_nutrition_entries_date ON nutrition_entries (date);

CREATE TABLE daily_activities (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                   UUID NOT NULL REFERENCES users(id),
    date                        DATE NOT NULL,
    steps                       INTEGER NOT NULL DEFAULT 0,
    estimated_active_calories   NUMERIC(7,2),
    estimated_total_calories    NUMERIC(7,2),
    source                      VARCHAR(20) NOT NULL DEFAULT 'ESTIMATED',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_daily_activities UNIQUE (client_id, date)
);
CREATE INDEX idx_daily_activities_client_id ON daily_activities (client_id);
