-- Apply once before starting with SPRING_PROFILES_ACTIVE=prod.
-- The backend intentionally uses ddl-auto=validate in production.

ALTER TABLE object_item
    ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(512);

ALTER TABLE object_item
    ADD COLUMN IF NOT EXISTS progress INTEGER NOT NULL DEFAULT 0;

ALTER TABLE join_application
    ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(255);

ALTER TABLE mind
    ADD COLUMN IF NOT EXISTS tracking_token_hash VARCHAR(64);

ALTER TABLE join_application
    ADD COLUMN IF NOT EXISTS tracking_token_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_mind_tracking_token_hash
    ON mind (tracking_token_hash);

CREATE INDEX IF NOT EXISTS idx_join_application_tracking_token_hash
    ON join_application (tracking_token_hash);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'object_item_progress_range'
    ) THEN
        ALTER TABLE object_item
            ADD CONSTRAINT object_item_progress_range
            CHECK (progress >= 0 AND progress <= 100);
    END IF;
END $$;
