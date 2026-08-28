-- Effect code 32: Ignore Block Chance (percentage stat).

UPDATE effect_definition
SET name = 'Ignore Block Chance',
    display_type = 'PERCENT_100'
WHERE code = 32;
