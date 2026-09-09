CREATE TABLE conversations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id    UUID NOT NULL REFERENCES users(id),
    client_id   UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversations UNIQUE (coach_id, client_id)
);

CREATE TABLE messages (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id   UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id         UUID NOT NULL REFERENCES users(id),
    content           TEXT NOT NULL,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);

CREATE TABLE notifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id),
    type          VARCHAR(30) NOT NULL,
    title         VARCHAR(200) NOT NULL,
    body          TEXT,
    related_type  VARCHAR(50),
    related_id    UUID,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_id ON notifications (user_id);

CREATE TABLE notification_preferences (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                        UUID NOT NULL REFERENCES users(id),
    workout_reminders              BOOLEAN NOT NULL DEFAULT TRUE,
    message_notifications          BOOLEAN NOT NULL DEFAULT TRUE,
    pr_notifications               BOOLEAN NOT NULL DEFAULT TRUE,
    checkin_reminders              BOOLEAN NOT NULL DEFAULT TRUE,
    coach_feedback_notifications   BOOLEAN NOT NULL DEFAULT TRUE,
    goal_milestone_notifications   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_preferences_user UNIQUE (user_id)
);
