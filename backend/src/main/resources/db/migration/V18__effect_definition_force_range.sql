-- Effect code 26: Force range (flat integer, not a percentage).

UPDATE effect_definition
SET name = 'Force range',
    display_type = 'FLAT'
WHERE code = 26;
