-- Frases da guild: o administrador cadastra a frase e liga a quem falou; cada membro vota em até 3.
-- said_by guarda o nome como foi anotado (aparece quando a frase ainda não tem membro ligado).
CREATE TABLE phrase (
    id            BIGSERIAL PRIMARY KEY,
    text          VARCHAR(300) NOT NULL,
    member_id     BIGINT       REFERENCES member(id) ON DELETE SET NULL,
    said_by       VARCHAR(100),
    said_at       TIMESTAMPTZ,
    created_by_id BIGINT       REFERENCES member(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ
);

-- Um voto por membro por frase; o limite de 3 votos por membro fica no serviço (com trava no membro).
CREATE TABLE phrase_vote (
    phrase_id  BIGINT      NOT NULL REFERENCES phrase(id) ON DELETE CASCADE,
    member_id  BIGINT      NOT NULL REFERENCES member(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (phrase_id, member_id)
);

CREATE INDEX idx_phrase_vote_member ON phrase_vote (member_id);

-- Frases iniciais (horário de Brasília). Liga ao membro com o mesmo nick, se existir.
INSERT INTO phrase (text, said_by, said_at, member_id)
SELECT v.text, v.said_by, v.said_at::timestamp AT TIME ZONE 'America/Sao_Paulo',
       (SELECT m.id FROM member m
         WHERE LOWER(m.nickname) = LOWER(v.said_by) AND m.status <> 'EXCLUIDO'
         ORDER BY m.id LIMIT 1)
FROM (VALUES
    (1,  'AMANHA É DIA 04, VOU SAIR COM A REBECA', 'meow', '2026-11-03 20:12'),
    (2,  'VOU PARAR 10M, PRA COMPRAR MAGIA Q FALTA PRO DUDU', 'jony', '2026-11-04 10:46'),
    (3,  'QUERO UM TRANSLATOR Q EU DIGITAR E JA TRADUZIR INSTA', 'Xonz', '2026-11-04 13:46'),
    (4,  'MINHA POTE ESTA ACABANDO A INFINITA', 'xHide', '2026-11-04 18:08'),
    (5,  'Bolo de chocolate q minha tia trouxe', 'juninho', '2026-11-05 19:09'),
    (6,  'não dormi não, tava tomando banho', 'duffita', '2026-11-06 01:26'),
    (7,  'amanha já pego lvl 50 e já faço as quests vermelhas', 'juninho', '2026-11-06 01:38'),
    (8,  'eu comia macarraum na escola', 'opsnao', '2026-11-06 15:56'),
    (9,  'prepara pra fazer cp ai, quando tiver 1% o chip', 'opsnao', '2026-11-06 16:24'),
    (10, 'tive que desentupir a privada, porque minha irmã deu descarga com um pano', 'lipao', '2026-11-06 01:33'),
    (11, 'mais pote inifinita não, pra vc não ficar batendo igual um abestado', 'juninho', '2026-11-06 21:55'),
    (12, 'NEM O CARA MAIS LIMPO É LIMPO', 'BABILONIA', '2026-11-06 22:55'),
    (13, 'o mercadão de peixe esta aberto', 'Dudu', '2026-11-07 00:44'),
    (14, 'vai liberar uma vaga pq o nico pegou lvl 48', 'nico', '2026-11-07 01:53'),
    (15, 'vou fazer 10 minutinhos de abobora', 'duffita', '2026-11-07 13:26'),
    (16, 'vou almoçar agora', 'lipao', '2026-11-07 14:30')
) AS v(ord, text, said_by, said_at)
ORDER BY v.ord;
