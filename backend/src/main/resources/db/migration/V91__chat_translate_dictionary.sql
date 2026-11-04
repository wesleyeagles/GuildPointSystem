-- Dicionário do tradutor do chat, para gastar cada vez menos a cota do MyMemory.
-- chat_translation: tudo que a API já traduziu, pela mensagem normalizada (minúsculas, espaços e pontuação
-- repetida reduzidos, ё = е). Mensagem repetida nunca volta para a API.
CREATE TABLE chat_translation (
    source      VARCHAR(400) PRIMARY KEY,
    translation VARCHAR(1000) NOT NULL,
    hits        INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- chat_glossary: gírias e termos do jogo editados pela liderança (Admin -> DLL). Têm prioridade sobre a
-- API: cada termo é trocado pela palavra em inglês antes de traduzir; mensagem só com termos do
-- glossário nem chama a API.
CREATE TABLE chat_glossary (
    id          BIGSERIAL PRIMARY KEY,
    source      VARCHAR(100) NOT NULL UNIQUE,
    translation VARCHAR(100) NOT NULL,
    updated_by  BIGINT REFERENCES member(id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO chat_glossary(source, translation) VALUES
    ('хил', 'heal'), ('хила', 'heal'), ('хилл', 'heal'), ('хилер', 'healer'), ('хилера', 'healer'),
    ('хильни', 'heal me'), ('хилни', 'heal me'),
    ('бафф', 'buff'), ('баф', 'buff'), ('бафы', 'buffs'), ('баффы', 'buffs'), ('бафни', 'buff me'),
    ('дебафф', 'debuff'), ('дебаф', 'debuff'),
    ('рес', 'res'), ('реса', 'res'), ('ресни', 'res me'), ('реснуть', 'res'), ('ресните', 'res me'),
    ('го', 'let''s go'), ('гоу', 'let''s go'),
    ('пати', 'party'), ('пт', 'party'), ('лут', 'loot'), ('нуб', 'noob'), ('нубы', 'noobs'),
    ('спс', 'thanks'), ('пасиб', 'thanks'), ('плз', 'please'), ('пж', 'please'), ('пжл', 'please'),
    ('кд', 'cooldown'), ('хп', 'HP'), ('мп', 'MP'), ('фп', 'FP'), ('лаг', 'lag'), ('лагает', 'lagging'),
    ('афк', 'AFK'), ('данж', 'dungeon'), ('инвайт', 'invite'), ('инв', 'invite'), ('кинь инв', 'send invite'),
    ('гильдия', 'guild'), ('ги', 'guild'), ('кв', 'quest'), ('шмот', 'gear'), ('дроп', 'drop'),
    ('лвл', 'level'), ('лвла', 'level'), ('ап', 'level up'), ('танк', 'tank'), ('дд', 'DD'),
    ('ок', 'ok'), ('кк', 'ok'), ('норм', 'fine'), ('лол', 'lol'), ('хз', 'no idea'), ('щас', 'right now'),
    ('спок', 'good night'), ('гг', 'GG');
