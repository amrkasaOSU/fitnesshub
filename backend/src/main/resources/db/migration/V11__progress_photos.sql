-- Transformation photos. Stored as bytea rather than on disk or in object
-- storage: the target deploy platforms give containers an ephemeral filesystem
-- (anything written locally is lost on redeploy), and object storage would mean
-- a third-party account and credentials this project deliberately doesn't
-- require. At a few photos per client per month with a 5 MB cap this is
-- comfortably within what Postgres handles well. If a coach ever scales to
-- hundreds of clients, move the bytes to object storage and keep this table as
-- the metadata index - the API contract wouldn't change.
CREATE TABLE progress_photos (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id    UUID NOT NULL REFERENCES users(id),
    taken_on     DATE NOT NULL,
    caption      TEXT,
    content_type VARCHAR(64) NOT NULL,
    size_bytes   INTEGER NOT NULL,
    image_data   BYTEA NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Whitelist rather than blacklist: only formats a browser renders inline,
    -- so a stored file can never be something executable.
    CONSTRAINT chk_progress_photos_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT chk_progress_photos_size
        CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);

CREATE INDEX idx_progress_photos_client ON progress_photos (client_id, taken_on DESC);
