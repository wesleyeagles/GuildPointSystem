-- Relics (V120) no inventário da guild, como o V41 fez para o resto do catálogo:
-- um item MISC por relic, para aparecer na aba Itens e no seletor do leilão.

INSERT INTO item (type, name, rarity, image_url, game_code, lua_category, created_by_id, deleted)
SELECT 'MISC', g.name, 'Relic', NULL, LOWER(g.game_code), g.category,
       (SELECT id FROM member WHERE role = 'LIDER' ORDER BY id LIMIT 1), FALSE
FROM game_lua_item g
WHERE g.category = 'RELIC'
  AND NOT EXISTS (
      SELECT 1 FROM item i
      WHERE i.deleted = FALSE AND LOWER(i.game_code) = LOWER(g.game_code)
  )
ORDER BY g.game_index;

INSERT INTO item_misc (item_id)
SELECT i.id
FROM item i
WHERE i.deleted = FALSE AND i.lua_category = 'RELIC'
  AND NOT EXISTS (SELECT 1 FROM item_misc m WHERE m.item_id = i.id);
