ALTER TABLE member
    ADD COLUMN IF NOT EXISTS level INTEGER NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_member_level'
    ) THEN
        ALTER TABLE member
            ADD CONSTRAINT chk_member_level CHECK (level >= 1 AND level <= 99);
    END IF;
END $$;
