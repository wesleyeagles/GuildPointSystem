-- Accessory catalog schema + effect definitions

CREATE TABLE effect_definition (
    code         INT PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    display_type VARCHAR(20)  NOT NULL DEFAULT 'PERCENT_100',
    CONSTRAINT chk_effect_display_type CHECK (display_type IN ('PERCENT_100', 'FLAT', 'BOOLEAN'))
);

CREATE TABLE game_accessory (
    id             BIGSERIAL PRIMARY KEY,
    game_code      VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(200) NOT NULL,
    subtype        VARCHAR(20)  NOT NULL,
    icon_id        INT          NOT NULL DEFAULT 0,
    sprite_sheet   VARCHAR(500) NOT NULL DEFAULT '/sprites/ringseamulets.png',
    grade          INT          NOT NULL DEFAULT 0,
    civil_mask     VARCHAR(20)  NOT NULL DEFAULT '11111000',
    level_required INT          NOT NULL DEFAULT 0,
    fire           INT          NOT NULL DEFAULT 0,
    water          INT          NOT NULL DEFAULT 0,
    soil           INT          NOT NULL DEFAULT 0,
    wind           INT          NOT NULL DEFAULT 0,
    eff_code_1     INT,
    eff_unit_1     NUMERIC(10,6),
    eff_code_2     INT,
    eff_unit_2     NUMERIC(10,6),
    eff_code_3     INT,
    eff_unit_3     NUMERIC(10,6),
    eff_code_4     INT,
    eff_unit_4     NUMERIC(10,6),
    CONSTRAINT chk_ga_subtype CHECK (subtype IN ('RING', 'AMULET'))
);

CREATE INDEX idx_game_accessory_subtype ON game_accessory (subtype);
CREATE INDEX idx_game_accessory_grade ON game_accessory (grade);
CREATE INDEX idx_game_accessory_name ON game_accessory (name);

CREATE TABLE item_set (
    id          BIGSERIAL PRIMARY KEY,
    set_code    VARCHAR(50) NOT NULL,
    civil_mask  VARCHAR(20),
    head        VARCHAR(50),
    upper       VARCHAR(50),
    lower       VARCHAR(50),
    shoes       VARCHAR(50),
    gauntlet    VARCHAR(50),
    weapon      VARCHAR(50),
    shield      VARCHAR(50),
    amul1       VARCHAR(50),
    amul2       VARCHAR(50),
    ring1       VARCHAR(50),
    ring2       VARCHAR(50),
    cloack      VARCHAR(50),
    eff1_code   INT,
    eff1_unit   NUMERIC(10,6),
    eff2_code   INT,
    eff2_unit   NUMERIC(10,6),
    eff3_code   INT,
    eff3_unit   NUMERIC(10,6),
    eff4_code   INT,
    eff4_unit   NUMERIC(10,6),
    eff5_code   INT,
    eff5_unit   NUMERIC(10,6),
    eff6_code   INT,
    eff6_unit   NUMERIC(10,6),
    eff7_code   INT,
    eff7_unit   NUMERIC(10,6),
    eff8_code   INT,
    eff8_unit   NUMERIC(10,6)
);

CREATE INDEX idx_item_set_code ON item_set (set_code);

INSERT INTO effect_definition (code, name, display_type) VALUES
    (1, 'SP Max', 'PERCENT_100'),
    (2, 'FP Consumption', 'PERCENT_100'),
    (3, 'Accuracy', 'FLAT'),
    (4, 'Dodge', 'FLAT'),
    (5, 'HP/FP Max', 'PERCENT_100'),
    (6, 'All Attack Power', 'PERCENT_100'),
    (7, 'Defense', 'PERCENT_100'),
    (8, 'Skill Level', 'FLAT'),
    (10, 'Detect', 'BOOLEAN'),
    (12, 'Speed', 'FLAT'),
    (14, 'Unknown Effect 14', 'FLAT'),
    (17, 'Attack Damage to HP', 'PERCENT_100'),
    (20, 'Range', 'FLAT'),
    (22, 'Debuff Assisting Time', 'PERCENT_100');
