-- Pontos de RG dados nos 30 min antes de uma CW: no máximo 2 por membro por dia. Cada um fica aqui para contar.
-- RG fora dessa janela não tem limite e não entra nesta tabela.
CREATE TABLE rg_award (
    id        BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    day       DATE NOT NULL,                                  -- dia da CW (as 3 CWs do dia caem no mesmo dia)
    cw_slot   SMALLINT NOT NULL CHECK (cw_slot BETWEEN 1 AND 3),
    points    INT NOT NULL,
    actor_id  BIGINT REFERENCES member(id) ON DELETE SET NULL,
    at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rg_award_member_day ON rg_award (member_id, day);
