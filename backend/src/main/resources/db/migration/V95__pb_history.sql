-- Histórico de PB: a DLL de todo membro aprovado acompanha os bosses desta lista na lista de monstros do jogo.
-- Enquanto vê o boss vivo, avisa a cada ~10 s (presença); quando o jogo marca a morte, avisa na hora.
-- Presente no PB = viu o boss vivo E estava perto na morte. Página só de Administrador+. Só histórico (sem pontos).

-- Bosses acompanhados, pelo nome como o jogo escreve (sem o "[nível]" do começo).
-- kind: ROTACAO = PB da rotação (HQ / Colônia / Middle); EXTRA = boss de campo, EPB etc.
CREATE TABLE pb_boss (
    name     VARCHAR(64) PRIMARY KEY,
    label    VARCHAR(64),
    kind     VARCHAR(16) NOT NULL CHECK (kind IN ('ROTACAO', 'EXTRA')),
    added_by BIGINT REFERENCES member(id) ON DELETE SET NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Nomes de monstros vistos nos minutos depois de cada horário de PB: a liderança escolhe quais são bosses.
CREATE TABLE pb_candidate (
    name          VARCHAR(64) PRIMARY KEY,
    map_name      VARCHAR(64),
    seen_count    INT NOT NULL DEFAULT 1,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    ignored       BOOLEAN NOT NULL DEFAULT false
);

-- Cada vez que um boss nasceu (serial do jogo), do primeiro membro que o viu até a morte.
CREATE TABLE pb_kill (
    id            BIGSERIAL PRIMARY KEY,
    boss_name     VARCHAR(64) NOT NULL,
    serial        BIGINT NOT NULL,
    map_code      INT NOT NULL,
    map_name      VARCHAR(64),
    x             INT NOT NULL,
    z             INT NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at  TIMESTAMPTZ NOT NULL,
    died_at       TIMESTAMPTZ,
    min_hp        SMALLINT CHECK (min_hp BETWEEN 0 AND 100),
    found_by      BIGINT REFERENCES member(id) ON DELETE SET NULL
);
CREATE INDEX idx_pb_kill_serial ON pb_kill(map_code, serial, boss_name);
CREATE INDEX idx_pb_kill_seen ON pb_kill(first_seen_at DESC);

-- Quem viu o boss vivo (primeira e última vez) e se estava perto quando ele morreu.
CREATE TABLE pb_presence (
    kill_id       BIGINT NOT NULL REFERENCES pb_kill(id) ON DELETE CASCADE,
    member_id     BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at  TIMESTAMPTZ NOT NULL,
    saw_death     BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (kill_id, member_id)
);
CREATE INDEX idx_pb_presence_member ON pb_presence(member_id);
