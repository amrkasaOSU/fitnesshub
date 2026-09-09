CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,
    profile_image_url TEXT,
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    last_login_at   TIMESTAMPTZ,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'COACH', 'CLIENT'))
);
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));

CREATE TABLE coach_profiles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_name     VARCHAR(255),
    bio               TEXT,
    specialties       VARCHAR(500),
    years_experience  INTEGER,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_coach_profiles_user UNIQUE (user_id)
);

CREATE TABLE client_profiles (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    coach_id               UUID NOT NULL REFERENCES users(id),
    date_of_birth          DATE,
    height_cm              NUMERIC(6,2),
    sex                    VARCHAR(10),
    fitness_goal           VARCHAR(30) NOT NULL DEFAULT 'GENERAL_FITNESS',
    activity_level         VARCHAR(30) NOT NULL DEFAULT 'MODERATELY_ACTIVE',
    target_weight          NUMERIC(6,2),
    starting_weight        NUMERIC(6,2),
    daily_calorie_target   NUMERIC(7,2),
    daily_protein_target   NUMERIC(6,2),
    daily_step_target      INTEGER NOT NULL DEFAULT 10000,
    unit_system            VARCHAR(10) NOT NULL DEFAULT 'IMPERIAL',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_client_profiles_user UNIQUE (user_id),
    CONSTRAINT chk_client_profiles_goal CHECK (fitness_goal IN
        ('FAT_LOSS','MUSCLE_GAIN','STRENGTH','GENERAL_FITNESS','ATHLETIC_PERFORMANCE','RECOMPOSITION'))
);
CREATE INDEX idx_client_profiles_coach_id ON client_profiles (coach_id);

CREATE TABLE coach_clients (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id    UUID NOT NULL REFERENCES users(id),
    client_id   UUID NOT NULL REFERENCES users(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at    TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_coach_clients UNIQUE (coach_id, client_id),
    CONSTRAINT chk_coach_clients_status CHECK (status IN ('ACTIVE','PAUSED','ENDED'))
);
CREATE INDEX idx_coach_clients_coach_id ON coach_clients (coach_id);
CREATE INDEX idx_coach_clients_client_id ON coach_clients (client_id);

CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id  UUID,
    action         VARCHAR(50) NOT NULL,
    target_type    VARCHAR(50),
    target_id      UUID,
    metadata       TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
