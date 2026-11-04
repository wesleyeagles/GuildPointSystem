-- Uso diário do tradutor do chat (dia de Brasília), para acompanhar a cota do MyMemory no Admin -> DLL.
-- O MyMemory não informa quanto da cota já foi usado; o site conta o que mandou para ele.
CREATE TABLE chat_translate_usage (
    day           DATE PRIMARY KEY,
    api_chars     BIGINT NOT NULL DEFAULT 0,  -- caracteres enviados ao MyMemory (é o que a cota conta)
    api_calls     BIGINT NOT NULL DEFAULT 0,
    saved_hits    BIGINT NOT NULL DEFAULT 0,  -- mensagens respondidas pelo dicionário salvo
    glossary_hits BIGINT NOT NULL DEFAULT 0,  -- mensagens respondidas só pelo glossário
    failed        BIGINT NOT NULL DEFAULT 0   -- API falhou ou estava pausada
);
