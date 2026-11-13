-- Limites de cada mapa do jogo no mundo (X e Z), como a janela do mapa do jogo usa para desenhar o personagem:
-- a DLL lê da janela do mapa (carregada junto com o mapa) e envia uma vez por mapa no histórico de PB.
-- O site usa para pôr a bolinha do boss na imagem do mapa; sem linha aqui, usa os limites tirados do cliente.
CREATE TABLE game_map_bounds (
    map_code   INT PRIMARY KEY,
    min_x      INT NOT NULL,
    min_z      INT NOT NULL,
    max_x      INT NOT NULL,
    max_z      INT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (min_x < max_x AND min_z < max_z)
);
