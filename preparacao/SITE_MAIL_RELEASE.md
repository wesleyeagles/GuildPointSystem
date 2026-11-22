# Publicação conjunta: cartas em lote e histórico de lances

Este pacote reúne o envio de teleportes, a recompensa de evento e o diagnóstico do histórico de lances. O PR #67 já está na `main`; suas rotas e a migration V141 também precisam estar no backend que estiver rodando em produção.

## Uma única publicação do backend

1. Mesclar o PR deste pacote após os checks. Usar a `main` resultante no backend.
2. No Coolify da aplicação `backend.guildsystem.com.br`, fazer **um deploy do backend** da `main` atualizada. Preservar banco, JWT_SECRET, variáveis e volume de uploads; não recriar banco ou editar lances.
3. Confirmar o startup e a execução/validação da migration **V141__auction_bid_cancellation.sql**. Os módulos de envio deste pacote não adicionam outra migration.
4. Confirmar a publicação do frontend correspondente na Vercel. O pipeline CI somente compila/testa; ele não aciona o Coolify. Um frontend publicado não comprova atualização do backend.
5. Conferir as três rotas abaixo antes de liberar o uso. São consultas OPTIONS sem login, sem Origin e sem corpo: não dão lances, cancelam lances, selecionam destinatários ou enviam cartas.

```bash
curl -sS -X OPTIONS -D - -o /dev/null https://backend.guildsystem.com.br/api/auctions/0/bids
# HTTP 200; Allow deve incluir GET e POST.
curl -sS -X OPTIONS -D - -o /dev/null https://backend.guildsystem.com.br/api/auctions/0/bids/0/cancel
# HTTP 200; Allow deve incluir POST.
curl -sS -X OPTIONS -D - -o /dev/null https://backend.guildsystem.com.br/api/events/claims/recipients
# HTTP 200; Allow deve incluir POST.
```

Os IDs zero não precisam existir: o teste consulta os métodos registrados, sem executar a ação. Não adicionar `Origin` para não confundir a resposta com o preflight genérico de CORS. No diagnóstico de 21/11/2026, produção permitia somente POST na primeira rota e retornava 404 na segunda: ainda rodava o backend anterior ao PR #67, embora o health estivesse UP.

O painel encontrado nesta máquina foi `http://backend.guildsystem.com.br:8000/login`; a API administrativa exige autenticação. Não incluir senhas ou tokens neste documento/repositório. O acesso ao painel é de Wesley.

## Fluxos disponíveis

- **Equipamentos → Gerenciar players → Enviar teleportes:** nível exatamente 50 pré-selecionado, remetente digitável (padrão meow), Taraven/Ether com checkbox e quantidade por personagem. O remetente fica fora dos destinatários.
- **Dashboard → Últimos pontos → Enviar recompensa:** admins selecionam quem efetivamente ganhou pontos na rodada; qualquer nível. Remetente digitável (padrão boi); item exclusivamente `ipcss04`, a mesma versão usada no lote #049. IDs de resgate são revalidados imediatamente antes do planejamento.
- **Leilão → Gerenciar lances:** histórico e cancelamento individual do PR #67. A tela agora distingue backend desatualizado de outros erros e preserva a mensagem/código de erro do servidor.

## Ponte e cliente privados

As cartas em lote dependem da ponte local existente na porta 15183 e da variante privada compatível do cliente. Nenhum executável, fonte da DLL privada, instalador, diário de envios ou função de dividir loot acompanha este PR. Wesley publica somente site/backend; não deve substituir a DLL do operador por uma versão pública.

A ponte valida o EDF e o estoque antes de começar. Cada carta precisa ter destinatário, item e quantidade exatos e confirmação de sucesso antes da próxima. Incerteza interrompe o lote e impede repetição automática. Pausar/cancelar vale entre cartas; fechar o navegador não interrompe um lote que já tenha sido autorizado.

O diário de envio é **local ao PC da ponte**, não um histórico distribuído entre admins. Preservá-lo para evitar repetição de recompensas. Não usar dois PCs para distribuir os mesmos resgates sem conciliação manual.

A prévia local continua em modo simulado. Não configurar `VITE_MAIL_SIMULATION_ONLY=true` no frontend de produção: esse modo exige a ponte de simulação e bloqueia envios reais. No site oficial, toda autorização de iniciar um lote continua manual.

## Validação sem efeitos reais

- Frontend: seleção/quantidades, exclusão do remetente, permissões, revalidação de resgates, interrupção sem retry, diário por claimId, estados simulados e regressões da interface existente.
- Backend: permissão ADMINISTRADOR/LÍDER, resgates inválidos/negados/sem pontos, rodadas distintas, PB automático e regressões de cancelamento de lances, em banco isolado.
- API: checagem das rotas por OPTIONS e saúde do preview. Ponte: somente consulta de status; nenhuma carta nova ou lote real iniciado.
- O teste operacional de envio real em jogo não faz parte desta publicação. Histórico detalhado e limites: [teleportes](EQUIPMENT_TELEPORT_MAIL.md) e [recompensas](EVENT_REWARD_MAIL.md).
