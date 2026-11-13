-- Ultima vez que o personagem fez algo mesmo parado no lugar: HP/FP/SP caiu entre dois envios (usou skill ou
-- apanhou) ou a DLL (1.30.0+) viu tecla, clique, roda do mouse ou camera girando no jogo. Conta contra o AFK
-- (CW e Pontos de RG): quem fica parado batendo no chip nao e AFK.
ALTER TABLE character_status
    ADD COLUMN active_at TIMESTAMPTZ;
