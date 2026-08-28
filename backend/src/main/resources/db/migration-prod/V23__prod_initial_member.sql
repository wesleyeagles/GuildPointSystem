-- Production-only seed: wipe dev/test members and create the initial guild leader.
-- Applied only when spring.flyway.locations includes classpath:db/migration-prod (prod profile).

TRUNCATE TABLE member RESTART IDENTITY CASCADE;

INSERT INTO member (email, password_hash, nickname, race_id, class_id, role, status, profile_complete, points)
VALUES (
    'crafael.wesley@gmail.com',
    '$2b$10$HM0shBB0bybS9oL8R62JUej.ndPlWAoTX/EwpC77eGL3cGhH9/WO.',
    'Artorpode',
    (SELECT id FROM game_race WHERE name = 'Cora'),
    (SELECT cc.id
     FROM character_class cc
              JOIN game_race gr ON gr.id = cc.race_id
     WHERE gr.name = 'Cora'
       AND cc.name = 'Templar Knight'),
    'LIDER',
    'APROVADO',
    TRUE,
    0
);
