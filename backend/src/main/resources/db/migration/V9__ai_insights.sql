CREATE TABLE ai_insight_requests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    client_id   UUID REFERENCES users(id),
    question    TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_insight_requests_user_id ON ai_insight_requests (user_id);

CREATE TABLE ai_insight_responses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id   UUID NOT NULL REFERENCES ai_insight_requests(id) ON DELETE CASCADE,
    response     TEXT NOT NULL,
    model        VARCHAR(50) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_insight_responses_request_id ON ai_insight_responses (request_id);
