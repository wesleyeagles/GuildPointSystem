-- Quantas vezes a DLL enviou esse par mapa/regiao. Logo depois de um teleporte a DLL pode
-- mandar o mapa antigo com a regiao nova uma vez (ex.: Cora HQ + Death Valley); o par certo
-- se repete a cada envio. O modal so mostra pares vistos 2+ vezes, cada regiao no mapa mais visto.
ALTER TABLE game_region ADD COLUMN seen_count INTEGER NOT NULL DEFAULT 1;

-- Sem historico: visto uma vez so (primeiro = ultimo) fica 1, o resto conta como 2.
UPDATE game_region SET seen_count = 2 WHERE last_seen_at > first_seen_at;
