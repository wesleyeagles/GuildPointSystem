ALTER TABLE member
    ADD COLUMN level INTEGER NOT NULL DEFAULT 1;

ALTER TABLE member
    ADD CONSTRAINT chk_member_level CHECK (level >= 1 AND level <= 99);
