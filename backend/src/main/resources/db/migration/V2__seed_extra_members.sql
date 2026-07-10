-- Extra guild members for local/dev testing (password: 123456)
-- Points spread from 0 to ~9500 for dashboard, auctions, and rankings.

UPDATE member SET points = 10000 WHERE email = 'teste@teste.com.br';
UPDATE member SET points = 5200  WHERE email = 'teste2@teste.com.br';
UPDATE member SET points = 850    WHERE email = 'teste3@teste.com.br';

INSERT INTO member (email, password_hash, nickname, race_id, class_id, role, status, profile_complete, points) VALUES
    (
        'voidstriker@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'VoidStriker',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Punisher'),
        'MEMBRO', 'APROVADO', TRUE, 1250
    ),
    (
        'novaphoenix@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'NovaPhoenix',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Wizard'),
        'MEMBRO', 'APROVADO', TRUE, 3400
    ),
    (
        'crystalfang@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'CrystalFang',
        (SELECT id FROM game_race WHERE name = 'Cora'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Cora' AND cc.name = 'Assassin'),
        'MEMBRO', 'APROVADO', TRUE, 890
    ),
    (
        'ironwarden@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'IronWarden',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Shield Miller'),
        'MODERADOR', 'APROVADO', TRUE, 4800
    ),
    (
        'shadowbyte@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'ShadowByte',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Phantom Shadow'),
        'MEMBRO', 'APROVADO', TRUE, 175
    ),
    (
        'arcanepulse@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'ArcanePulse',
        (SELECT id FROM game_race WHERE name = 'Cora'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Cora' AND cc.name = 'Warlock'),
        'MEMBRO', 'APROVADO', TRUE, 2100
    ),
    (
        'bladerunner@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'BladeRunner',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Berserker'),
        'MEMBRO', 'APROVADO', TRUE, 450
    ),
    (
        'neoncipher@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'NeonCipher',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Scientist'),
        'MEMBRO', 'APROVADO', TRUE, 7850
    ),
    (
        'frostnova@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'FrostNova',
        (SELECT id FROM game_race WHERE name = 'Cora'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Cora' AND cc.name = 'Grazier'),
        'MEMBRO', 'APROVADO', TRUE, 320
    ),
    (
        'thundercore@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'ThunderCore',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Astralist'),
        'MEMBRO', 'APROVADO', TRUE, 1560
    ),
    (
        'darkmatter@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'DarkMatter',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Dementer'),
        'MEMBRO', 'APROVADO', TRUE, 9200
    ),
    (
        'silverlance@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'SilverLance',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Sentinel'),
        'MEMBRO', 'APROVADO', TRUE, 670
    ),
    (
        'mysticveil@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'MysticVeil',
        (SELECT id FROM game_race WHERE name = 'Cora'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Cora' AND cc.name = 'Dark Priest'),
        'MEMBRO', 'APROVADO', TRUE, 4150
    ),
    (
        'rifthunter@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'RiftHunter',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Striker'),
        'MEMBRO', 'APROVADO', TRUE, 0
    ),
    (
        'pulseknight@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'PulseKnight',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Armor Rider'),
        'MEMBRO', 'APROVADO', TRUE, 2890
    ),
    (
        'echoblade@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'EchoBlade',
        (SELECT id FROM game_race WHERE name = 'Cora'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Cora' AND cc.name = 'Stealer'),
        'MEMBRO', 'APROVADO', TRUE, 1100
    ),
    (
        'quantumshift@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'QuantumShift',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Battle Leader'),
        'MEMBRO', 'APROVADO', TRUE, 6350
    ),
    (
        'stormcaller@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'StormCaller',
        (SELECT id FROM game_race WHERE name = 'Cora'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Cora' AND cc.name = 'Artist'),
        'MEMBRO', 'PENDENTE', TRUE, 540
    ),
    (
        'ghostprotocol@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'GhostProtocol',
        (SELECT id FROM game_race WHERE name = 'Bellato'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Bellato' AND cc.name = 'Infiltrator'),
        'MEMBRO', 'APROVADO', TRUE, 3750
    ),
    (
        'terraforge@guild.test',
        '$2a$10$BVZX6Sj2951h2iK.q0yZj.JV8QB9YAcAcv.aCIujO.McXS6hA1e8q',
        'TerraForge',
        (SELECT id FROM game_race WHERE name = 'Accretia'),
        (SELECT cc.id FROM character_class cc
            JOIN game_race gr ON gr.id = cc.race_id
            WHERE gr.name = 'Accretia' AND cc.name = 'Assaulter'),
        'MEMBRO', 'APROVADO', TRUE, 1980
    );

-- Initial point grants (audit trail for seeded balances)
INSERT INTO points_adjustment (member_id, actor_id, amount, modality, reason)
SELECT m.id, leader.id, m.points, 'AJUSTE', 'Pontos iniciais (seed V2)'
FROM member m
CROSS JOIN (SELECT id FROM member WHERE email = 'teste@teste.com.br') leader
WHERE m.points > 0
  AND NOT EXISTS (
      SELECT 1 FROM points_adjustment pa
      WHERE pa.member_id = m.id AND pa.reason = 'Pontos iniciais (seed V2)'
  );
