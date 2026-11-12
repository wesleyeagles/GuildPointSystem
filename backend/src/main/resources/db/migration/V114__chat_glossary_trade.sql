-- Termos de comércio do chat no glossário do tradutor. Com a DLL 1.38.0 a mensagem com link de item é traduzida
-- pedaço por pedaço ("Продам " antes do link); sozinho, "продам" voltava da API como "15 000 р." e ficou salvo.
INSERT INTO chat_glossary(source, translation) VALUES
    ('продам', 'WTS'), ('продаю', 'WTS'), ('куплю', 'WTB'), ('покупаю', 'WTB'), ('обменяю', 'WTT'), ('обмен', 'trade')
ON CONFLICT (source) DO NOTHING;

-- Traduções salvas com esses termos são refeitas na próxima vez (como ao editar o glossário no Admin).
DELETE FROM chat_translation
WHERE strpos(source, 'продам') > 0 OR strpos(source, 'продаю') > 0 OR strpos(source, 'куплю') > 0
   OR strpos(source, 'покупаю') > 0 OR strpos(source, 'обмен') > 0;
