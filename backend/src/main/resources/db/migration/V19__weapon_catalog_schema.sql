-- Weapon catalog schema

CREATE TABLE game_weapon (
    id             BIGSERIAL PRIMARY KEY,
    game_code      VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(200) NOT NULL,
    weapon_type    VARCHAR(30)  NOT NULL,
    icon_id        INT          NOT NULL DEFAULT 0,
    sprite_sheet   VARCHAR(500) NOT NULL,
    sprite_cols    INT          NOT NULL DEFAULT 64,
    grade          INT          NOT NULL DEFAULT 0,
    civil_mask     VARCHAR(20)  NOT NULL DEFAULT '11111',
    level_required INT          NOT NULL DEFAULT 0,
    ga_min_af      INT          NOT NULL DEFAULT 0,
    ga_max_af      INT          NOT NULL DEFAULT 0,
    ma_min_af      INT          NOT NULL DEFAULT 0,
    ma_max_af      INT          NOT NULL DEFAULT 0,
    eff_code_1     INT,
    eff_unit_1     NUMERIC(10, 6),
    eff_code_2     INT,
    eff_unit_2     NUMERIC(10, 6),
    eff_code_3     INT,
    eff_unit_3     NUMERIC(10, 6),
    eff_code_4     INT,
    eff_unit_4     NUMERIC(10, 6),
    CONSTRAINT chk_game_weapon_type CHECK (weapon_type IN ('KNIFE', 'SWORD', 'AXE', 'HAMMER', 'SPEAR', 'BOW', 'FIREARM', 'LAUNCHER', 'THROWING_KNIFE', 'STAFF', 'MINING_TOOL', 'GRENADE_LAUNCHER'))
);

CREATE INDEX idx_game_weapon_type ON game_weapon (weapon_type);
CREATE INDEX idx_game_weapon_level ON game_weapon (level_required);
