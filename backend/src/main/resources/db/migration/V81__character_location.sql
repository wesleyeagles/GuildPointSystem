-- Onde o personagem estava no ultimo envio da DLL (mapa, regiao do mini-mapa e grade X/Y).
-- location_at = quando o local foi recebido; null = cliente sem leitura de local.
ALTER TABLE character_status
    ADD COLUMN map_name    VARCHAR(64),
    ADD COLUMN region_name VARCHAR(64),
    ADD COLUMN map_x       INTEGER,
    ADD COLUMN map_y       INTEGER,
    ADD COLUMN location_at TIMESTAMPTZ;

CREATE INDEX idx_character_status_location_at ON character_status (location_at);
