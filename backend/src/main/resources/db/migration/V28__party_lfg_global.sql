ALTER TABLE party_lfg DROP CONSTRAINT IF EXISTS chk_party_lfg_map;
DROP INDEX IF EXISTS idx_party_lfg_map;
ALTER TABLE party_lfg DROP COLUMN IF EXISTS map;
