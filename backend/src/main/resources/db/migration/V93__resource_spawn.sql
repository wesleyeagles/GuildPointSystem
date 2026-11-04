-- Mapa de recursos (flores, minério e cactos): a DLL de todo membro aprovado registra sozinha onde cada
-- recurso apareceu (posição do próprio mob, não do jogador). Só nos mapas 2 (Crag Mind Field),
-- 10 (Ether of Heaven) e 11 (Sette Desert). Os pontos ficam para sempre.

-- Quem VÊ os pontos (no jogo e no site). Todo membro aprovado com a DLL envia; Administrador+ monta a lista e sempre vê no site.
CREATE TABLE resource_tracker (
    member_id BIGINT PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
    added_by  BIGINT REFERENCES member(id),
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Nome do mob como o jogo escreve -> grau. A DLL manda todo mob sem "[nível]" no nome nesses mapas;
-- nome que não está aqui entra sozinho com tier NULL (desconhecido) e a liderança escolhe o grau.
-- tier: 1..3 = grau; 0 = não é recurso (ignorado: não grava nem mostra).
CREATE TABLE resource_mob (
    name          VARCHAR(64) PRIMARY KEY,
    map_code      INT NOT NULL,
    tier          SMALLINT CHECK (tier BETWEEN 0 AND 3),
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO resource_mob(name, map_code, tier) VALUES
    ('Icy Flower', 10, 1), ('Frosty Fern', 10, 2), ('Heart of Ice', 10, 3),
    ('Mithril Deposits', 2, 1), ('Orichalcum Deposits', 2, 2), ('Heart of Caves', 2, 3),
    ('Cactus', 11, 1), ('Spiky Cactus', 11, 2), ('Heart of Sand', 11, 3);

-- Cada recurso achado: um registro por mob (serial do jogo), mesmo que vários membros o vejam.
CREATE TABLE resource_spawn (
    id        BIGSERIAL PRIMARY KEY,
    map_code  INT NOT NULL,
    mob_name  VARCHAR(64) NOT NULL REFERENCES resource_mob(name) ON UPDATE CASCADE,
    serial    BIGINT NOT NULL,
    x         INT NOT NULL,
    z         INT NOT NULL,
    found_at  TIMESTAMPTZ NOT NULL,
    found_by  BIGINT REFERENCES member(id) ON DELETE SET NULL
);
CREATE INDEX idx_resource_spawn_serial ON resource_spawn(map_code, serial);
CREATE INDEX idx_resource_spawn_map ON resource_spawn(map_code, id);

-- Escala da imagem do mapa grande (400x400 do cliente) em unidades do mundo, como a janela do mapa do jogo
-- calcula: px = offX + (x - minX) * scaleX; py = offY + (maxZ - z) * scaleY. A DLL manda o que leu do jogo.
CREATE TABLE resource_map_view (
    map_code   INT PRIMARY KEY,
    area_w     INT NOT NULL,
    area_h     INT NOT NULL,
    off_x      INT NOT NULL,
    off_y      INT NOT NULL,
    min_x      INT NOT NULL,
    max_z      INT NOT NULL,
    scale_x    DOUBLE PRECISION NOT NULL,
    scale_y    DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Sette Desert, lido da janela do mapa do jogo em 04/11/2026 (cliente e0e2b6e7).
INSERT INTO resource_map_view(map_code, area_w, area_h, off_x, off_y, min_x, max_z, scale_x, scale_y)
VALUES (11, 400, 400, 31, 0, -5198, 6339, 0.03051706962287426, 0.030415937304496765);
