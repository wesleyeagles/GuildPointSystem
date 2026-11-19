-- Ajustes visuais da DLL escolhidos no Admin (linha única). party_arrow_scale / guild_arrow_scale: tamanho das
-- setas da PT e da guild do jogo (alvo compartilhado, efeitos 54155/54156), em % do original; a DLL lê nos
-- headers do shout.
CREATE TABLE dll_display_settings (
    id                SMALLINT    PRIMARY KEY DEFAULT 1,
    party_arrow_scale INTEGER     NOT NULL DEFAULT 100,
    guild_arrow_scale INTEGER     NOT NULL DEFAULT 100,
    updated_by_id     BIGINT      REFERENCES member(id),
    updated_at        TIMESTAMPTZ,
    CONSTRAINT chk_dll_display_settings_single CHECK (id = 1),
    CONSTRAINT chk_dll_display_party_arrow CHECK (party_arrow_scale BETWEEN 50 AND 500),
    CONSTRAINT chk_dll_display_guild_arrow CHECK (guild_arrow_scale BETWEEN 50 AND 500)
);

INSERT INTO dll_display_settings (id) VALUES (1);
