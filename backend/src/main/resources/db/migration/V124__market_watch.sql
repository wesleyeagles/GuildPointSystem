-- Alerta de item no market da Cerberus: Admin/Líder escolhe membro -> item (com upgrade mínimo e preço máximo
-- por unidade opcionais). O site lê o market de tempos em tempos e, quando aparece um anúncio que serve,
-- guarda um alerta; a DLL do membro mostra o card no jogo.
CREATE TABLE market_watch (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    item_code   VARCHAR(16)  NOT NULL CHECK (item_code ~ '^[a-z][a-z0-9]{3,11}$'),
    item_name   VARCHAR(200) NOT NULL,
    min_upgrade SMALLINT     CHECK (min_upgrade BETWEEN 0 AND 7),
    max_price   BIGINT       CHECK (max_price > 0),
    created_by  BIGINT       REFERENCES member(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_market_watch_code ON market_watch(item_code);

-- Anúncios que já viraram alerta (para não repetir enquanto continuam à venda).
CREATE TABLE market_watch_seen (
    watch_id    BIGINT      NOT NULL REFERENCES market_watch(id) ON DELETE CASCADE,
    listing_key VARCHAR(80) NOT NULL,
    seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (watch_id, listing_key)
);

CREATE TABLE market_alert (
    id         BIGSERIAL PRIMARY KEY,
    watch_id   BIGINT       NOT NULL REFERENCES market_watch(id) ON DELETE CASCADE,
    member_id  BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    item_code  VARCHAR(16)  NOT NULL,
    item_name  VARCHAR(200) NOT NULL,
    upgrade    SMALLINT     NOT NULL,
    quantity   INT          NOT NULL,
    price      BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_market_alert_member ON market_alert(member_id, created_at);
