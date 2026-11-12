-- "English Player": a DLL deste membro mostra tudo em inglês (painéis, avisos e shouts), em vez de PT-BR.
ALTER TABLE member ADD COLUMN english_player BOOLEAN NOT NULL DEFAULT FALSE;
