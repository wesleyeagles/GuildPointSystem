-- RF Online races
CREATE TABLE game_race (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

-- RF Online classes (level 40 advancements, per race)
CREATE TABLE character_class (
    id        BIGSERIAL PRIMARY KEY,
    race_id   BIGINT       NOT NULL REFERENCES game_race(id),
    name      VARCHAR(50)  NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    UNIQUE (race_id, name)
);

CREATE TABLE member (
    id                BIGSERIAL PRIMARY KEY,
    email             VARCHAR(255) UNIQUE,
    password_hash     VARCHAR(255),
    discord_id        VARCHAR(50) UNIQUE,
    nickname          VARCHAR(100) NOT NULL,
    avatar_url        VARCHAR(500),
    race_id           BIGINT REFERENCES game_race(id),
    class_id          BIGINT REFERENCES character_class(id),
    role              VARCHAR(20)  NOT NULL DEFAULT 'MEMBRO',
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDENTE',
    points            BIGINT       NOT NULL DEFAULT 0,
    profile_complete  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_member_role CHECK (role IN ('MEMBRO', 'MODERADOR', 'ADMINISTRADOR', 'LIDER')),
    CONSTRAINT chk_member_status CHECK (status IN ('PENDENTE', 'APROVADO', 'REJEITADO'))
);

CREATE UNIQUE INDEX idx_single_leader ON member (role) WHERE role = 'LIDER';

CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(50) NOT NULL,
    actor_id   BIGINT REFERENCES member(id),
    target_id  BIGINT REFERENCES member(id),
    payload    JSONB       NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_created ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_type ON audit_log (type);

CREATE TABLE objective (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    points          BIGINT       NOT NULL,
    type            VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    daily_limit     INT,
    created_by_id   BIGINT       NOT NULL REFERENCES member(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by_id   BIGINT REFERENCES member(id),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_objective_type CHECK (type IN ('NORMAL', 'LIMITADO'))
);

CREATE TABLE guild_event (
    id              BIGSERIAL PRIMARY KEY,
    objective_id    BIGINT       NOT NULL REFERENCES objective(id),
    password        VARCHAR(4)   NOT NULL,
    duration_minutes INT         NOT NULL,
    created_by_id   BIGINT       NOT NULL REFERENCES member(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE event_claim (
    id           BIGSERIAL PRIMARY KEY,
    event_id     BIGINT      NOT NULL REFERENCES guild_event(id),
    member_id    BIGINT      NOT NULL REFERENCES member(id),
    objective_id BIGINT      NOT NULL REFERENCES objective(id),
    points       BIGINT      NOT NULL,
    claimed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    claimed_date DATE GENERATED ALWAYS AS ((claimed_at AT TIME ZONE 'UTC')::date) STORED,
    denied       BOOLEAN     NOT NULL DEFAULT FALSE,
    denied_by_id BIGINT REFERENCES member(id),
    denied_at    TIMESTAMPTZ,
    UNIQUE (event_id, member_id)
);

CREATE INDEX idx_event_claim_daily ON event_claim (member_id, objective_id, claimed_date);

CREATE TABLE points_adjustment (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT      NOT NULL REFERENCES member(id),
    actor_id    BIGINT      NOT NULL REFERENCES member(id),
    amount      BIGINT      NOT NULL,
    modality    VARCHAR(20) NOT NULL,
    reason      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_points_modality CHECK (modality IN ('AJUSTE', 'LEILAO', 'EVENTO', 'REVERSAO'))
);

CREATE TABLE item (
    id           BIGSERIAL PRIMARY KEY,
    type         VARCHAR(20)  NOT NULL,
    name         VARCHAR(200),
    rarity       VARCHAR(50),
    image_url    VARCHAR(500) NOT NULL,
    description  TEXT,
    created_by_id BIGINT      NOT NULL REFERENCES member(id),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_item_type CHECK (type IN ('WEAPON', 'ARMOR', 'ACCESSORY', 'MISC'))
);

CREATE TABLE item_weapon (
    item_id           BIGINT PRIMARY KEY REFERENCES item(id) ON DELETE CASCADE,
    level             INT    NOT NULL DEFAULT 0,
    subtype           VARCHAR(50) NOT NULL,
    attack_min        INT    NOT NULL DEFAULT 0,
    attack_max        INT    NOT NULL DEFAULT 0,
    force_attack_min  INT    NOT NULL DEFAULT 0,
    force_attack_max  INT    NOT NULL DEFAULT 0,
    cast_name         VARCHAR(100),
    special_effects   JSONB  NOT NULL DEFAULT '[]'
);

CREATE TABLE item_armor (
    item_id                 BIGINT PRIMARY KEY REFERENCES item(id) ON DELETE CASCADE,
    level                   INT    NOT NULL DEFAULT 0,
    subtype                 VARCHAR(50) NOT NULL,
    armor_class             VARCHAR(50) NOT NULL,
    avg_def_power           INT    NOT NULL DEFAULT 0,
    defense_success_rate    INT    NOT NULL DEFAULT 0,
    special_effects         JSONB  NOT NULL DEFAULT '[]'
);

CREATE TABLE item_accessory (
    item_id         BIGINT PRIMARY KEY REFERENCES item(id) ON DELETE CASCADE,
    race_id         BIGINT NOT NULL REFERENCES game_race(id),
    subtype         VARCHAR(20) NOT NULL,
    special_effects JSONB  NOT NULL DEFAULT '[]',
    CONSTRAINT chk_accessory_subtype CHECK (subtype IN ('RING', 'AMULET'))
);

CREATE TABLE item_misc (
    item_id BIGINT PRIMARY KEY REFERENCES item(id) ON DELETE CASCADE
);

CREATE TABLE item_talic (
    id         BIGSERIAL PRIMARY KEY,
    item_id    BIGINT      NOT NULL REFERENCES item(id) ON DELETE CASCADE,
    talic_type VARCHAR(50) NOT NULL,
    level      INT         NOT NULL DEFAULT 0 CHECK (level BETWEEN 0 AND 7),
    slot       INT         NOT NULL DEFAULT 0,
    UNIQUE (item_id, talic_type)
);

CREATE TABLE auction (
    id               BIGSERIAL PRIMARY KEY,
    duration_minutes INT         NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by_id    BIGINT      NOT NULL REFERENCES member(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at          TIMESTAMPTZ NOT NULL,
    current_bid      BIGINT      NOT NULL DEFAULT 0,
    winner_id        BIGINT REFERENCES member(id),
    tie_break_seed   BIGINT,
    dole_phase       INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_auction_status CHECK (status IN ('OPEN', 'DOLE', 'TIE_BREAK', 'CLOSED'))
);

CREATE TABLE auction_item (
    id         BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auction(id) ON DELETE CASCADE,
    item_id    BIGINT NOT NULL REFERENCES item(id),
    quantity   INT    NOT NULL DEFAULT 1
);

CREATE TABLE auction_bid (
    id         BIGSERIAL PRIMARY KEY,
    auction_id BIGINT      NOT NULL REFERENCES auction(id) ON DELETE CASCADE,
    member_id  BIGINT      NOT NULL REFERENCES member(id),
    amount     BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auction_bid_auction ON auction_bid (auction_id, amount DESC, created_at DESC);

CREATE TABLE auction_message (
    id         BIGSERIAL PRIMARY KEY,
    auction_id BIGINT      NOT NULL REFERENCES auction(id) ON DELETE CASCADE,
    member_id  BIGINT REFERENCES member(id),
    type       VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    content    TEXT        NOT NULL,
    image_url  VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_auction_msg_type CHECK (type IN ('TEXT', 'BID', 'BOT', 'IMAGE'))
);

-- Seeds: races and level 40 classes (wiki.rfdatabase.net)
INSERT INTO game_race (name) VALUES
    ('Accretia'), ('Bellato'), ('Cora');

INSERT INTO character_class (race_id, name, image_url) VALUES
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Berserker', '/classes/berserker.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Armsman', '/classes/armsman.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Shield Miller', '/classes/shield-miller.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Hidden Soldier', '/classes/hidden-soldier.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Sentinel', '/classes/sentinel.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Infiltrator', '/classes/infiltrator.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Wizard', '/classes/wizard.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Astralist', '/classes/astralist.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Holy Chandra', '/classes/holy-chandra.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Mental Smith', '/classes/mental-smith.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Bellato'), 'Armor Rider', '/classes/armor-rider.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Templar Knight', '/classes/templar-knight.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Guardian', '/classes/guardian.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Black Knight', '/classes/black-knight.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Adventurer', '/classes/adventurer.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Stealer', '/classes/stealer.gif'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Assassin', '/classes/assassin.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Warlock', '/classes/warlock.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Dark Priest', '/classes/dark-priest.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Grazier', '/classes/grazier.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Cora'), 'Artist', '/classes/artist.gif'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Punisher', '/classes/punisher.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Assaulter', '/classes/assaulter.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Mercenary', '/classes/mercenary.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Striker', '/classes/striker.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Dementer', '/classes/dementer.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Phantom Shadow', '/classes/phantom-shadow.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Scientist', '/classes/scientist.jpg'),
    ((SELECT id FROM game_race WHERE name = 'Accretia'), 'Battle Leader', '/classes/battle-leader.jpg');

-- Test accounts (password: 123456)
INSERT INTO member (email, password_hash, nickname, race_id, class_id, role, status, profile_complete) VALUES
    (
        'teste@teste.com.br',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'Teste Lider',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Mercenary'),
        'LIDER',
        'APROVADO',
        TRUE
    ),
    (
        'teste2@teste.com.br',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'Teste Administrador',
        (SELECT id FROM game_race WHERE name = 'Cora'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Cora' AND cc.name = 'Warlock'),
        'ADMINISTRADOR',
        'APROVADO',
        TRUE
    ),
    (
        'teste3@teste.com.br',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'Teste Membro',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Armsman'),
        'MEMBRO',
        'APROVADO',
        TRUE
    );
