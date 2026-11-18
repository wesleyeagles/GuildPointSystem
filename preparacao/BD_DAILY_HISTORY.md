# BD da guilda

Histórico aditivo por personagem, quest e dia civil de Brasília. A tabela `bd_daily_completion` (V133) é independente do `quest_status.done`, que pode conter simples remoções do journal.

- `POST /api/quest-status/bd/ingest`: sinal Q070613, definição validada, slot anterior 0..39 e novo slot anterior+103, observedAt em segundos UTC. Reenvio idempotente, não apaga ao reaceitar. API dedicada evita confirmação falsa de recebimento por um backend antigo que ignorasse campos novos.
- `GET /api/quests/bd?day=YYYY-MM-DD`: sessão de membro aprovado, perfil completo, não excluído/suspenso. Lista como o roster, deduplicada pelo nome.
- `GET /api/quest-status/bd/game?character=...`: mesmo gate por personagem do roster da DLL; TSV BD1, dia, nome e HH:mm:ss ou `-`. Consulta apenas de visualização.
- Q394782 permanece pendente; nunca interpretamos remoção como conclusão.

O horário é da observação; não comprova o instante exato da conclusão nem horário de reset da quest. Ausência de registro não indica que o membro não a fez. O cadastro name-based é o contrato legado da DLL, não autenticação do servidor RF; não concede pontos/recompensas.

Frontend: aba BD da guilda em Quests, calendário, consulta a cada 30s enquanto montada. Testes: BdDailyServiceTest, QuestGuildDaily.test.tsx e Quests.test.tsx. Migração validada em PostgreSQL local separado e rotas testadas com personagem fictício.

A fonte/binário da DLL e todas as extensões privadas ficam fora desta alteração do site. Publicação e instalação não executadas.
