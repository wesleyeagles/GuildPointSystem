# Rascunho do leilão

Administradores podem selecionar **Usar como destino na RF-ui** na tela de leilões.
Com uma ponte local compatível em execução, Ctrl + Alt + clique esquerdo no item
acrescenta uma unidade ao rascunho. Repetir o clique soma a quantidade. O botão
**Criar Leilão** continua sendo necessário para publicar.

**Copiar leilão**, em um leilão aberto, preenche o formulário com os itens,
quantidades e o encerramento exato do original. Não copia lances, vencedor,
chat ou pontos. Alterar a duração ou escolher um início agendado descarta o
encerramento copiado. O backend rejeita encerramentos vencidos, além de 24 horas
ou combinados com agendamento; criação normal e agendamento continuam iguais.

## Integração local opcional

O site não instala nem distribui DLLs. A ponte local é uma dependência externa;
sem ela, o formulário manual continua funcionando normalmente. A conexão começa
somente por ação do administrador e termina ao desconectar, enviar o formulário
ou sair da página. O navegador pode solicitar permissão de rede local.

Contrato HTTP: POST `http://127.0.0.1:15184/draft`, JSON `{ action, key, ids? }`.
`key` é aleatória (32 caracteres hexadecimais), exclusiva do rascunho.
- `select`: seleciona esse rascunho; resposta 2xx.
- `read`: renova a sessão e retorna `[{ id, code }]`; 409 indica outro destino.
- `ack`: confirma IDs processados. Falhas de confirmação não repetem itens.
- `close`: libera o destino. Nenhum token do site é enviado para a ponte.

A ponte deve escutar somente em loopback, aceitar apenas origens autorizadas,
expirar destinos inativos e confirmar eventos por ID. Esta mudança contém
apenas o consumidor web, sem a implementação da ponte ou de módulos do jogo.

O catálogo é resolvido no servidor por GET `/api/items/auction-code/{code}`,
restrito a administradores. Só aceita código alfanumérico de sete caracteres,
ignora registros excluídos e rejeita códigos ausentes ou ambíguos. Não cria itens
nem escolhe variantes por aproximação.

## Validação

- Frontend: `npm test` e `npm run build`.
- Backend: `./mvnw test -Dtest=AuctionDraftTest,AuctionItemLookupTest`.
- Testes cobrem permissões, falha de ACK, payload inválido, mudança de destino,
  fechamento da página, cópia editável e compatibilidade com agendamento.
