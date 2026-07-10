CREATE TABLE item_seed (
    id         BIGSERIAL PRIMARY KEY,
    category   VARCHAR(50)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    UNIQUE (category, name)
);

CREATE INDEX idx_item_seed_category ON item_seed (category, sort_order);

INSERT INTO item_seed (category, name, sort_order) VALUES
    ('WEAPON_RARITY', 'Normal', 1),
    ('WEAPON_RARITY', 'Purple', 2),
    ('WEAPON_RARITY', 'Intense', 3),
    ('WEAPON_RARITY', 'Orange', 4),
    ('WEAPON_RARITY', 'Leon', 5),
    ('WEAPON_RARITY', 'Relic', 6),
    ('WEAPON_RARITY', 'PVP', 7),
    ('WEAPON_RARITY', 'Special', 8),
    ('WEAPON_RARITY', 'Event', 9),

    ('ARMOR_RARITY', 'Normal', 1),
    ('ARMOR_RARITY', 'Intense', 2),
    ('ARMOR_RARITY', 'Orange', 3),
    ('ARMOR_RARITY', 'Superior', 4),
    ('ARMOR_RARITY', 'Hero', 5),

    ('WEAPON_SUBTYPE', 'Axe', 1),
    ('WEAPON_SUBTYPE', 'Mace', 2),
    ('WEAPON_SUBTYPE', 'Staff', 3),
    ('WEAPON_SUBTYPE', 'Spear', 4),
    ('WEAPON_SUBTYPE', 'Bow', 5),
    ('WEAPON_SUBTYPE', 'Crossbow', 6),
    ('WEAPON_SUBTYPE', 'Firearm', 7),
    ('WEAPON_SUBTYPE', 'Launcher', 8),
    ('WEAPON_SUBTYPE', 'Grenade Launcher', 9),
    ('WEAPON_SUBTYPE', 'Knife', 10),
    ('WEAPON_SUBTYPE', 'Sword', 11),
    ('WEAPON_SUBTYPE', 'Throwing Knife', 12),

    ('ARMOR_SUBTYPE', 'Helmet', 1),
    ('ARMOR_SUBTYPE', 'Upper', 2),
    ('ARMOR_SUBTYPE', 'Lower', 3),
    ('ARMOR_SUBTYPE', 'Gloves', 4),
    ('ARMOR_SUBTYPE', 'Shoes', 5),

    ('ARMOR_CLASS', 'Warrior', 1),
    ('ARMOR_CLASS', 'Ranger', 2),
    ('ARMOR_CLASS', 'Force', 3),
    ('ARMOR_CLASS', 'Launcher', 4),

    ('ACCESSORY_SUBTYPE', 'Ring', 1),
    ('ACCESSORY_SUBTYPE', 'Amulet', 2);
