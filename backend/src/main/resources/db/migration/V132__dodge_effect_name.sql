-- A kbase escreve a esquiva como "Damage avoidance 10"; no jogo é "Dodge +10" (ex.: Elemental Mild Armlet).
UPDATE item_weapon SET special_effects = regexp_replace(special_effects::text, 'Damage avoidance (-?)([0-9])',
    'Dodge \1\2', 'g')::jsonb WHERE special_effects::text LIKE '%Damage avoidance %';
UPDATE item_armor SET special_effects = regexp_replace(special_effects::text, 'Damage avoidance (-?)([0-9])',
    'Dodge \1\2', 'g')::jsonb WHERE special_effects::text LIKE '%Damage avoidance %';
UPDATE item_accessory SET special_effects = regexp_replace(special_effects::text, 'Damage avoidance (-?)([0-9])',
    'Dodge \1\2', 'g')::jsonb WHERE special_effects::text LIKE '%Damage avoidance %';
-- Positivo ganha o "+" como no jogo ("Dodge 10" -> "Dodge +10"; "Dodge -5" fica).
UPDATE item_weapon SET special_effects = regexp_replace(special_effects::text, '"Dodge ([0-9])', '"Dodge +\1', 'g')::jsonb
    WHERE special_effects::text LIKE '%"Dodge %';
UPDATE item_armor SET special_effects = regexp_replace(special_effects::text, '"Dodge ([0-9])', '"Dodge +\1', 'g')::jsonb
    WHERE special_effects::text LIKE '%"Dodge %';
UPDATE item_accessory SET special_effects = regexp_replace(special_effects::text, '"Dodge ([0-9])', '"Dodge +\1', 'g')::jsonb
    WHERE special_effects::text LIKE '%"Dodge %';
