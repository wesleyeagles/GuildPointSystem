-- Marcas de raid (como no WoW): 1 estrela, 2 círculo, 3 losango, 4 triângulo, 5 lua, 6 quadrado, 7 X, 8 caveira.
-- Cada marca fica em no máximo um integrante da raid.
ALTER TABLE raid_member ADD COLUMN mark SMALLINT CHECK (mark BETWEEN 1 AND 8);
CREATE UNIQUE INDEX uq_raid_member_mark ON raid_member(raid_id, mark) WHERE mark IS NOT NULL;
