-- Preço do market da Cerberus no tooltip de item (pela DLL): só os membros desta lista usam.
-- O Líder monta a lista no site (Admin -> DLL). Começa só com o Líder.
CREATE TABLE market_price_user (
    member_id BIGINT PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
    added_by  BIGINT REFERENCES member(id),
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO market_price_user(member_id) SELECT id FROM member WHERE role = 'LIDER';
