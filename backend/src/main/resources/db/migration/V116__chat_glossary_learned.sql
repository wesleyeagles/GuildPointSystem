-- Glossário que aprende sozinho: quando a API traduz uma mensagem de uma palavra só, a palavra entra no
-- glossário como "aprendida" (learned). Frase em que todas as palavras já estão no glossário (manuais ou
-- aprendidas) é montada sem chamar a API. Termo salvo à mão no Admin deixa de ser aprendido.
ALTER TABLE chat_glossary ADD COLUMN learned BOOLEAN NOT NULL DEFAULT FALSE;

-- O que o dicionário já tem de palavra solta em russo vira termo aprendido (tradução de até 3 palavras,
-- sem pontuação no fim; "Hello" vira "hello", siglas como "WTS" ficam).
INSERT INTO chat_glossary(source, translation, learned)
SELECT source, CASE WHEN t = upper(t) THEN t ELSE lower(left(t, 1)) || substr(t, 2) END, TRUE
FROM (SELECT source, btrim(regexp_replace(translation, '[.!?,;:]+$', '')) AS t FROM chat_translation
      WHERE source ~ '^[а-яё]+$' AND length(source) <= 100) s
WHERE t <> '' AND length(t) <= 100 AND lower(t) <> source
  AND array_length(regexp_split_to_array(t, '\s+'), 1) <= 3
ON CONFLICT (source) DO NOTHING;
