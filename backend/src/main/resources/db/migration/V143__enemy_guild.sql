-- Radar de guilds inimigas: o Admin/Líder cadastra o nome exato da guild no jogo (maiúsculas contam: o jogo
-- tem guilds com nome parecido, como "BIackLisT" com I). A DLL dos membros avisa quando vê alguém dessas guilds
-- por perto e o site devolve os pontos vermelhos no mapa do jogo para todos.
CREATE TABLE enemy_guild (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(16) NOT NULL UNIQUE CHECK (name ~ '^[\x21-\x7e]{1,16}$'),
    created_by BIGINT      REFERENCES member(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
