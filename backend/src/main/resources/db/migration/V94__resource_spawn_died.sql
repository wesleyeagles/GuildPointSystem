-- Quando o recurso morreu: a DLL de quem estava perto avisa quando ele some da lista de monstros.
-- Quem vê os pontos para de piscar e mostra o ponto vermelho piscante por 5 s.
ALTER TABLE resource_spawn ADD COLUMN died_at TIMESTAMPTZ;

CREATE INDEX idx_resource_spawn_died ON resource_spawn(map_code, died_at) WHERE died_at IS NOT NULL;
