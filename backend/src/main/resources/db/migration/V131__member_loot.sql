-- Loot dos membros: a DLL (1.49.0+) lê o chat do sistema do jogo ("X has acquired ...", "X has obtained N ...",
-- "Compensation item for X - ...") e manda para o site. Só fica o loot de membro; o mesmo anúncio visto por
-- vários membros vira uma linha só (member_loot_report diz quem já contou cada uma).
CREATE TABLE member_loot (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    -- Nome no jogo como veio no chat.
    looter      VARCHAR(32)  NOT NULL,
    -- DROP = anúncio do servidor (has acquired), PARTY = loot da PT (has obtained), REWARD = recompensa (Compensation).
    kind        VARCHAR(16)  NOT NULL,
    item        VARCHAR(120) NOT NULL,
    quantity    INT          NOT NULL DEFAULT 1 CHECK (quantity > 0),
    looted_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reported_by BIGINT       REFERENCES member(id) ON DELETE SET NULL
);
CREATE INDEX idx_member_loot_at ON member_loot(looted_at DESC);
CREATE INDEX idx_member_loot_member ON member_loot(member_id, looted_at DESC);

CREATE TABLE member_loot_report (
    loot_id     BIGINT NOT NULL REFERENCES member_loot(id) ON DELETE CASCADE,
    reporter_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    PRIMARY KEY (loot_id, reporter_id)
);
