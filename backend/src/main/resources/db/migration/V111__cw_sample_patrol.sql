-- Presença em CW: bot andando de um lado para o outro. Cada amostra guarda a coordenada, o FP e a última vez que
-- ganhou EXP; 5 min indo e voltando entre os mesmos pontos (até 5), sem usar skill (FP não cai) e sem EXP = AFK.
ALTER TABLE cw_sample ADD COLUMN map_x INT;
ALTER TABLE cw_sample ADD COLUMN map_y INT;
ALTER TABLE cw_sample ADD COLUMN fp INT;
ALTER TABLE cw_sample ADD COLUMN exp_gained_at TIMESTAMPTZ;
