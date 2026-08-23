-- Fix RF Online effect names and time-based display formatting.

ALTER TABLE effect_definition DROP CONSTRAINT IF EXISTS chk_effect_display_type;
ALTER TABLE effect_definition ADD CONSTRAINT chk_effect_display_type
    CHECK (display_type IN ('PERCENT_100', 'FLAT', 'BOOLEAN', 'SEC_MILLIS'));

UPDATE effect_definition SET name = 'Max FP', display_type = 'PERCENT_100' WHERE code = 16;
UPDATE effect_definition SET name = 'All Resistance', display_type = 'FLAT' WHERE code = 29;
UPDATE effect_definition SET name = 'Attack Delay of Launcher', display_type = 'SEC_MILLIS' WHERE code = 25;
UPDATE effect_definition SET name = 'Delay of Skill Attack', display_type = 'SEC_MILLIS' WHERE code = 34;
