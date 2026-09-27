ALTER TABLE party DROP CONSTRAINT IF EXISTS chk_party_map;
ALTER TABLE party_lfg DROP CONSTRAINT IF EXISTS chk_party_lfg_map;

UPDATE party SET map = 'ETHER' WHERE map = 'GERAL';
UPDATE party_lfg SET map = 'ETHER' WHERE map = 'GERAL';

ALTER TABLE party ADD COLUMN IF NOT EXISTS spot VARCHAR(200);

ALTER TABLE party ADD CONSTRAINT chk_party_map CHECK (map IN ('ETHER', 'CAULDRON', 'ELAN', 'MB', 'OC'));
ALTER TABLE party_lfg ADD CONSTRAINT chk_party_lfg_map CHECK (map IN ('ETHER', 'CAULDRON', 'ELAN', 'MB', 'OC'));
