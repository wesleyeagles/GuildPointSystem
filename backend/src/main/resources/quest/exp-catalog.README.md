# Catálogo EXP de Cora

35 quests dos NPCs `04137` (21) e `04138` (14), extraídas do cliente
`RF Cerberus - Stage Era/DataTable` em 30/10/2026. Os SHA-256 das fontes
estão em `exp-catalog.json`; nenhuma fonte do cliente é alterada.

- Definições: registros de 424 bytes de Quest.edf; código em 0x04,
  XP double em 0x70, seis recompensas (tabela, código, quantidade) em 0x88.
  `definitionHash` é o SHA-256 do registro inteiro.
- Níveis: eventos NPC de 336 bytes de Quest.edf, somente eventos ativos,
  referências confirmadas e raça Cora. Limites convertidos para inclusivos
  a partir das condições `level > lower` e `level < upper`.
- Nomes/recompensas: NDQuest, Item/NDItem; NPCs em Store/NDStore.
- XP por nível: bloco 5 de Character.edf, 66 registros de três doubles;
  a segunda coluna é Cora. A linha N contém a XP necessária para N → N+1.
  Percentual = 100 × XP bruta / XP do nível atual, sem multiplicadores.

Os leitores utilizados são `quest_binary`, `quest_context`, `quest_experience`
e `item_tables` do projeto irmão `edf_converter`.

O catálogo original e os hashes do monitor antigo permanecem intactos.
Hero Path tem o mesmo registro binário do catálogo antigo, mas seu texto
localizado mudou de EXP 10% para EXP 5%. Mantemos a identidade antiga para
compatibilidade; a interface remove essa anotação do título e apresenta a
recompensa atual extraída: uma EXP Potion 5%, sem XP direta. A faixa validada
dessa quest é 50–65; Deposit data é 45–49. Outras condições de aceitação do
jogo continuam sendo responsabilidade do NPC; o site filtra apenas nível.

`daily-details.json` complementa as 63 quests antigas com objetivos, itens e quantidades
das mesmas fontes Quest/Item/NDItem acima. Todas essas 63 definições têm XP
direta zero no cliente consultado; nenhuma recompensa de XP foi presumida.
A verificação de 31/10/2026 comparou as 66 linhas de Cora em Character.edf
(double no offset 8 de cada registro de 24 bytes) com `experienceByLevel`,
sem divergências. Os testes fixam percentuais nos níveis 40, 41, 45, 46, 47 e 49
para detectar uso indevido da linha anterior ou da próxima linha.

Os objetivos atuais substituem descrições localizadas antigas: ações de tipo 3
usam quantidade e nome de Character/NDCharacter; tipo 1 usa o NPC de Store/NDStore.
Para a ação de tipo 14 não interpretada, preservamos o objetivo anterior.
