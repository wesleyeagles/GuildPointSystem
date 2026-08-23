-- Additional RF Online effect codes used in accessory/set data but missing from V6 seeds.

INSERT INTO effect_definition (code, name, display_type) VALUES
    (9, 'Subtlety', 'BOOLEAN'),
    (11, 'Remove Protective Ability', 'BOOLEAN'),
    (13, 'Disclose Vulnerability', 'BOOLEAN'),
    (15, 'Magic Attack Power', 'PERCENT_100'),
    (16, 'FP Max', 'PERCENT_100'),
    (18, 'Attack Damage to FP', 'PERCENT_100'),
    (19, 'Critical Rate', 'FLAT'),
    (21, 'Mage Protection', 'PERCENT_100'),
    (23, 'SP Regen', 'FLAT'),
    (24, 'Dodge Rate', 'PERCENT_100'),
    (25, 'Reload Speed', 'FLAT'),
    (26, 'Mage Attack Power', 'PERCENT_100'),
    (27, 'Receive Critical Rate', 'PERCENT_100'),
    (28, 'Shield Block', 'FLAT'),
    (29, 'Elemental Attack', 'FLAT'),
    (30, 'HP Max', 'PERCENT_100'),
    (31, 'Negative Force Duration', 'PERCENT_100'),
    (32, 'Breaking Block', 'FLAT'),
    (33, 'Stealth', 'BOOLEAN'),
    (34, 'Avoid', 'FLAT')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    display_type = EXCLUDED.display_type;

UPDATE effect_definition SET name = 'OP Regen', display_type = 'FLAT' WHERE code = 14;
