-- Integrante oculto: a liderança acompanha o status dele, mas ele não vê a raid (sem painel no jogo,
-- fora dos convites e fora da raid no site para quem não gerencia).
ALTER TABLE raid_member ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
