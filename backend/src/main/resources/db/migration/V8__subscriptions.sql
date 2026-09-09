CREATE TABLE subscriptions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID NOT NULL REFERENCES users(id),
    type                     VARCHAR(20) NOT NULL,
    tier                     VARCHAR(20) NOT NULL DEFAULT 'FREE',
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    stripe_customer_id       VARCHAR(100),
    stripe_subscription_id   VARCHAR(100),
    current_period_end       TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscriptions_user UNIQUE (user_id)
);
CREATE INDEX idx_subscriptions_stripe_customer ON subscriptions (stripe_customer_id);
CREATE INDEX idx_subscriptions_stripe_subscription ON subscriptions (stripe_subscription_id);
