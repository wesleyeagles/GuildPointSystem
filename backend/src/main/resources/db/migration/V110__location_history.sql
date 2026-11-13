-- Histórico de local: cada envio de status da DLL (mais ou menos 1 por minuto por personagem), guardado por 3 dias.
-- Só para consulta da staff ("onde fulano estava às 21:52"): o character_status guarda apenas o último local.
CREATE TABLE location_history (
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    character_name VARCHAR(32),
    at             TIMESTAMPTZ NOT NULL,
    map_name       VARCHAR(64),
    region_name    VARCHAR(64),
    map_x          INT,
    map_y          INT,
    idle_since     TIMESTAMPTZ,       -- parado sem EXP desde (null = sem coordenada)
    dll_version    VARCHAR(16)
);
CREATE INDEX idx_location_history_at ON location_history (at);
CREATE INDEX idx_location_history_member_at ON location_history (member_id, at);
