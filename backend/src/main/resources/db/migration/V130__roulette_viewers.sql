-- Roleta: quem vê no jogo passa a ser escolhido (lista de membros ou todos), no lugar do "só o Líder" (V129).
ALTER TABLE roulette ADD COLUMN all_viewers BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE roulette_viewer (
    roulette_id BIGINT NOT NULL REFERENCES roulette(id) ON DELETE CASCADE,
    member_id   BIGINT NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    PRIMARY KEY (roulette_id, member_id)
);

-- Roletas antigas: "só o Líder" vira o Líder na lista; as abertas viram "todos".
INSERT INTO roulette_viewer(roulette_id, member_id)
SELECT r.id, m.id FROM roulette r JOIN member m ON m.role = 'LIDER' WHERE r.leaders_only;
UPDATE roulette SET all_viewers = TRUE WHERE NOT leaders_only;
ALTER TABLE roulette DROP COLUMN leaders_only;
