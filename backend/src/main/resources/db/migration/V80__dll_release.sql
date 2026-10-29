-- Versoes da DLL de sincronizacao (CerberusBags) publicadas pela administracao no site.
-- O arquivo fica no banco (poucos KB) para o download nao depender do disco do servidor.
CREATE TABLE dll_release (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(32) NOT NULL UNIQUE,
    notes TEXT,
    file_name VARCHAR(120) NOT NULL,
    file_data BYTEA NOT NULL,
    file_size INTEGER NOT NULL,
    published_by_id BIGINT REFERENCES member(id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Versao informada pela DLL no User-Agent ("CerberusBags/1.1.0") no ultimo envio do personagem.
ALTER TABLE character_status ADD COLUMN dll_version VARCHAR(32);
