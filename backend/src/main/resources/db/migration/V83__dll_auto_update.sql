-- Atualizacao automatica da DLL: o version.dll cru de cada versao, o sha256 e a assinatura
-- ECDSA P-256 (r||s em base64) sobre "CerberusBags-update\n<versao>\n<sha256>". A DLL so
-- se troca se a assinatura bater com a chave publica embutida nela. Versoes publicadas so
-- com o .rar (antes disto) ficam sem atualizacao automatica.
ALTER TABLE dll_release
    ADD COLUMN update_data      BYTEA,
    ADD COLUMN update_size      INTEGER,
    ADD COLUMN update_sha256    VARCHAR(64),
    ADD COLUMN update_signature VARCHAR(128);
