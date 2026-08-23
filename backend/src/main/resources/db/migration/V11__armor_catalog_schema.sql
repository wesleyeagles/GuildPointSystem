-- Armor catalog schema

CREATE TABLE game_armor (
    id             BIGSERIAL PRIMARY KEY,
    game_code      VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(200) NOT NULL,
    slot           VARCHAR(20)  NOT NULL,
    icon_id        INT          NOT NULL DEFAULT 0,
    sprite_sheet   VARCHAR(500) NOT NULL,
    sprite_cols    INT          NOT NULL DEFAULT 128,
    grade          INT          NOT NULL DEFAULT 0,
    civil_mask     VARCHAR(20)  NOT NULL DEFAULT '11111000',
    level_required INT          NOT NULL DEFAULT 0,
    def_fc         INT          NOT NULL DEFAULT 0,
    def_facing     NUMERIC(16, 12),
    eff_code_1     INT,
    eff_unit_1     NUMERIC(10, 6),
    eff_code_2     INT,
    eff_unit_2     NUMERIC(10, 6),
    eff_code_3     INT,
    eff_unit_3     NUMERIC(10, 6),
    eff_code_4     INT,
    eff_unit_4     NUMERIC(10, 6),
    CONSTRAINT chk_game_armor_slot CHECK (slot IN ('HELMET', 'UPPER', 'LOWER', 'GAUNTLET', 'SHOES'))
);

CREATE INDEX idx_game_armor_slot ON game_armor (slot);
CREATE INDEX idx_game_armor_level ON game_armor (level_required);
