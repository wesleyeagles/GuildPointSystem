-- Regioes do mini-mapa ja vistas pela DLL, por mapa. O jogo nao expoe a lista de
-- regioes; o catalogo cresce conforme a guild joga e alimenta o modal "Pontos por local".
CREATE TABLE game_region (
    map_name      VARCHAR(64) NOT NULL,
    region_name   VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (map_name, region_name)
);

INSERT INTO game_region (map_name, region_name, first_seen_at, last_seen_at)
SELECT map_name, region_name, min(location_at), max(location_at)
FROM character_status
WHERE map_name IS NOT NULL AND region_name IS NOT NULL AND location_at IS NOT NULL
GROUP BY map_name, region_name;
