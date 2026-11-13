-- Pontos automáticos dos PBs diários (o primeiro: PB Colônia das 02:00 de Brasília).
-- Shouts antes, Pontos de RG para quem está na base no horário do RG e o objetivo do PB para quem passou
-- pelas regiões do PB entre o RG e o fim da janela.

-- Uma rodada (dia de Brasília + código do PB): RG e objetivo já dados.
CREATE TABLE pb_auto_run (
    run_date     DATE NOT NULL,
    code         VARCHAR(32) NOT NULL,
    rg_at        TIMESTAMPTZ,                                       -- Pontos de RG já dados
    awarded_at   TIMESTAMPTZ,                                       -- objetivo do PB já dado
    objective_id BIGINT REFERENCES objective(id) ON DELETE SET NULL,
    PRIMARY KEY (run_date, code)
);

-- Quem a DLL viu nas regiões do PB durante a janela (um por membro).
CREATE TABLE pb_auto_presence (
    run_date       DATE NOT NULL,
    code           VARCHAR(32) NOT NULL,
    member_id      BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    first_seen_at  TIMESTAMPTZ NOT NULL,
    last_seen_at   TIMESTAMPTZ NOT NULL,
    character_name VARCHAR(32),
    region_name    VARCHAR(64),
    PRIMARY KEY (run_date, code, member_id)
);

-- Quem recebeu: RG = Pontos de RG na base; PB = objetivo do PB.
CREATE TABLE pb_auto_award (
    run_date  DATE NOT NULL,
    code      VARCHAR(32) NOT NULL,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    kind      VARCHAR(4) NOT NULL CHECK (kind IN ('RG', 'PB')),
    points    BIGINT NOT NULL,
    claim_id  BIGINT REFERENCES event_claim(id) ON DELETE SET NULL,
    at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_date, code, member_id, kind)
);
