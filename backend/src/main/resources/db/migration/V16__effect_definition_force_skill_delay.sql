-- Effect code 35: Force Skill Delay (armor/set data, millis-like units).

INSERT INTO effect_definition (code, name, display_type) VALUES
    (35, 'Force Skill Delay', 'SEC_MILLIS')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    display_type = EXCLUDED.display_type;
