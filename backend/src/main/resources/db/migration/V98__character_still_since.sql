-- Desde quando o personagem esta parado na mesma coordenada (mapa, regiao e grade X/Y).
-- Muda a cada envio com posicao diferente; null = sem local. Usado no "Pontos de RG" (AFK).
ALTER TABLE character_status
    ADD COLUMN still_since TIMESTAMPTZ;
