-- Ultima vez que o personagem ganhou EXP (level ou % subiu entre dois envios da DLL).
-- Null = ainda nao vimos ganho. Usado na lista de membros do jogo (lideres).
ALTER TABLE character_status
    ADD COLUMN exp_gained_at TIMESTAMPTZ;
