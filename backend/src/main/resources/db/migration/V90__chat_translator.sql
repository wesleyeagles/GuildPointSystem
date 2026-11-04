-- Tradutor do chat do jogo (russo -> inglês, pela DLL): só os membros desta lista usam.
-- Administrador+ monta a lista no site (Admin -> DLL).
CREATE TABLE chat_translator (
    member_id BIGINT PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE,
    added_by  BIGINT REFERENCES member(id),
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
