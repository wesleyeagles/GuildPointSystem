-- Distância das setas da PT e da guild até o alvo, em % da distância normal do jogo (100 = o centro da seta fica
-- onde ficaria no tamanho normal; menos = mais perto do alvo). A DLL 1.50.1+ lê no header do shout.
ALTER TABLE dll_display_settings
    ADD COLUMN arrow_distance INTEGER NOT NULL DEFAULT 100,
    ADD CONSTRAINT chk_dll_display_arrow_distance CHECK (arrow_distance BETWEEN 0 AND 200);
