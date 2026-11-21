-- Seta de marcação (DLL 1.52.0): Líder/Admin dá Ctrl+Shift+clique num jogador/mob e toda a guild vê uma seta 3D
-- igual à da PT, na cor escolhida aqui. marker_arrow_scale: tamanho em % (como as outras setas);
-- marker_color: cor RRGGBB que a DLL pinta na textura da seta.
ALTER TABLE dll_display_settings
    ADD COLUMN marker_arrow_scale INTEGER    NOT NULL DEFAULT 100,
    ADD COLUMN marker_color       VARCHAR(6) NOT NULL DEFAULT 'FF2828',
    ADD CONSTRAINT chk_dll_display_marker_arrow CHECK (marker_arrow_scale BETWEEN 50 AND 500),
    ADD CONSTRAINT chk_dll_display_marker_color CHECK (marker_color ~ '^[0-9A-F]{6}$');
