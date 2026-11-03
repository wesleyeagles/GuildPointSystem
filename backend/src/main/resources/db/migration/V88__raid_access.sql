-- Quem vê a aba Raid no site: cargo mínimo e uma lista de membros liberados mesmo abaixo dele.
-- Administrador e Líder sempre veem. O envio da DLL e o painel no jogo não dependem disso.
CREATE TABLE raid_settings (
    id         SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    min_role   VARCHAR(20) NOT NULL DEFAULT 'MEMBRO',
    updated_by BIGINT REFERENCES member(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO raid_settings(id) VALUES (1);

CREATE TABLE raid_viewer (
    member_id BIGINT PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
    added_by  BIGINT REFERENCES member(id),
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
