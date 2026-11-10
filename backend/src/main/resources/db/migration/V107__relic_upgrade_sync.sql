-- Relics sincronizadas pela DLL: upgrade (0 = base, 1..3 = "+1".."+3" no jogo) e a hora da última leitura.
ALTER TABLE member_equipment_relic ADD COLUMN upgrade SMALLINT NOT NULL DEFAULT 0 CHECK (upgrade BETWEEN 0 AND 3);
CREATE TABLE member_relic_sync (
    member_id BIGINT PRIMARY KEY REFERENCES member(id),
    synced_at TIMESTAMPTZ NOT NULL
);
