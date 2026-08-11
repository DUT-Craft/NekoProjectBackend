ALTER TABLE activity_registrations ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE activity_registrations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;
UPDATE activity_registrations SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE activity_registrations ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_activity_registrations_status ON activity_registrations(status, created_at);
