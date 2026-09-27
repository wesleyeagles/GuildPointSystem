ALTER TABLE party DROP CONSTRAINT IF EXISTS chk_party_map;
ALTER TABLE party ADD CONSTRAINT chk_party_map
    CHECK (map IN ('ETHER', 'CAULDRON', 'ELAN', 'MB', 'OC', 'SEM_MAPA'));
