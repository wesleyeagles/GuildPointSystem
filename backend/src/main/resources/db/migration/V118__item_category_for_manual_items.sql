-- Items created by hand (image upload or code outside the Lua catalog) had lua_category NULL
-- and never showed up in the inventory/auction tabs. Same rule as ItemService.inferCategory.

UPDATE item i
SET lua_category = 'WEAPON'
WHERE i.deleted = FALSE AND i.lua_category IS NULL AND i.type = 'WEAPON';

UPDATE item i
SET lua_category = UPPER(a.subtype)
FROM item_armor a
WHERE a.item_id = i.id AND i.deleted = FALSE AND i.lua_category IS NULL
  AND UPPER(a.subtype) IN ('HELMET', 'UPPER', 'LOWER', 'GLOVES', 'SHOES', 'SHIELD');

UPDATE item i
SET lua_category = UPPER(c.subtype)
FROM item_accessory c
WHERE c.item_id = i.id AND i.deleted = FALSE AND i.lua_category IS NULL
  AND UPPER(c.subtype) IN ('RING', 'AMULET');
