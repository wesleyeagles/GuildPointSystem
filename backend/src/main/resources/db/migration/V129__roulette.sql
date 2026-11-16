-- Roleta no jogo: Admin/Líder escolhe os membros e o prêmio/motivo; o site sorteia o vencedor na hora e a DLL
-- (1.47.0+) mostra a roleta girando no meio da tela, todos sincronizados pelo tempo desde a criação.
CREATE TABLE roulette (
    id           BIGSERIAL PRIMARY KEY,
    created_by   BIGINT      REFERENCES member(id) ON DELETE SET NULL,
    prize        VARCHAR(60) NOT NULL,
    -- Teste: só personagem de Líder vê a roleta no jogo.
    leaders_only BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Posição (0..n-1) do vencedor em roulette_entry.
    winner_index INT         NOT NULL CHECK (winner_index >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_roulette_created ON roulette(created_at);

CREATE TABLE roulette_entry (
    roulette_id BIGINT      NOT NULL REFERENCES roulette(id) ON DELETE CASCADE,
    position    INT         NOT NULL CHECK (position >= 0),
    member_id   BIGINT      REFERENCES member(id) ON DELETE SET NULL,
    -- Nick na hora do sorteio (é o que aparece na roleta).
    nickname    VARCHAR(100) NOT NULL,
    PRIMARY KEY (roulette_id, position)
);
