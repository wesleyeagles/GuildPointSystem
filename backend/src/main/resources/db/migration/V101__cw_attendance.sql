-- Presença em CW: tempo de cada membro no mapa da CW (Crag Mind Field) durante cada CW, pelo envio de status da
-- DLL (a cada ~1 min). active_seconds = andando ou ganhando EXP; afk_seconds = parado na mesma coordenada há mais
-- de 3 min sem EXP (não conta como presença). Só histórico (não dá pontos). Página só de Administrador+.
CREATE TABLE cw_attendance (
    cw_date        DATE NOT NULL,                                   -- dia (horário do servidor, Moscou) da CW
    cw_slot        SMALLINT NOT NULL CHECK (cw_slot BETWEEN 1 AND 3), -- CW1 06h, CW2 14h, CW3 22h (Moscou)
    member_id      BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    character_name VARCHAR(32),
    active_seconds INT NOT NULL DEFAULT 0,
    afk_seconds    INT NOT NULL DEFAULT 0,
    first_seen_at  TIMESTAMPTZ NOT NULL,                            -- primeiro envio no mapa da CW
    last_seen_at   TIMESTAMPTZ NOT NULL,                            -- último envio no mapa da CW
    -- Último envio durante a CW (em qualquer mapa) e se era no mapa da CW: o tempo entre dois envios só conta
    -- se os dois foram no mapa (quem sai para a base e volta não ganha o tempo fora).
    last_tick_at   TIMESTAMPTZ NOT NULL,
    in_map         BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (cw_date, cw_slot, member_id)
);
