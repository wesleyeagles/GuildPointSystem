-- Pontuação automática de CW. Troca o acumulado da V101 por amostras: cada envio de status da DLL perto e
-- durante a CW (mapa + desde quando está parado). O tempo em Crag Mind Field é calculado das amostras, cortado no
-- fim real da CW (avisado pela DLL ao ver "Due to the result of Chip War" no chat, ou marcado por um Admin).
DROP TABLE IF EXISTS cw_attendance;

CREATE TABLE cw_sample (
    member_id      BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    at             TIMESTAMPTZ NOT NULL,
    cw_date        DATE NOT NULL,                                     -- dia (horário do servidor, Moscou) da CW
    cw_slot        SMALLINT NOT NULL CHECK (cw_slot BETWEEN 1 AND 3), -- CW1 06h, CW2 14h, CW3 22h (Moscou)
    in_map         BOOLEAN NOT NULL,                                  -- estava em Crag Mind Field
    idle_since     TIMESTAMPTZ,                                       -- parado sem EXP desde (null = sem coordenada)
    character_name VARCHAR(32),
    PRIMARY KEY (member_id, at)
);
CREATE INDEX idx_cw_sample_cw ON cw_sample (cw_date, cw_slot, member_id, at);

-- Uma CW: fim real, pontuação e o bônus de 10 min antes.
CREATE TABLE cw_session (
    cw_date           DATE NOT NULL,
    cw_slot           SMALLINT NOT NULL CHECK (cw_slot BETWEEN 1 AND 3),
    ended_at          TIMESTAMPTZ,                       -- fim real da CW
    end_source        VARCHAR(8),                        -- GAME (DLL viu a mensagem) ou ADMIN
    ended_by          BIGINT REFERENCES member(id) ON DELETE SET NULL,
    closed_at         TIMESTAMPTZ,                       -- a janela acabou sem aviso de fim
    awarded_at        TIMESTAMPTZ,                       -- pontos da CW já dados
    objective_id      BIGINT REFERENCES objective(id) ON DELETE SET NULL,
    pre_awarded_at    TIMESTAMPTZ,                       -- bônus de 10 min antes já dado
    PRIMARY KEY (cw_date, cw_slot)
);

-- Quem recebeu: CW = objetivo "CW"/"CW Major" (60% da CW em Crag sem AFK); PRE = 2 pontos 10 min antes.
CREATE TABLE cw_award (
    cw_date   DATE NOT NULL,
    cw_slot   SMALLINT NOT NULL,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    kind      VARCHAR(4) NOT NULL CHECK (kind IN ('CW', 'PRE')),
    points    BIGINT NOT NULL,
    percent   INT,
    claim_id  BIGINT REFERENCES event_claim(id) ON DELETE SET NULL,
    at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (cw_date, cw_slot, member_id, kind)
);
