# Envio de teleportes para personagens nível 50

Em Equipamentos → Gerenciar players, **Enviar teleportes** abre o seletor de personagens exatamente nível 50. O remetente é digitável e começa em `meow`; o próprio remetente é excluído dos destinatários. Cada tipo tem checkbox e quantidade por carta de 1 a 255.

O botão e o histórico desta ferramenta aparecem somente para administradores. O processamento usa a ponte local já existente em `127.0.0.1:15183`, com origem exata autorizada. Não há nova rota no backend, alteração dos slots nem cálculo de GS.

## Conferência e processamento

- `teleport-plan`: lê o Item.edf atual e confere a bag antes de criar o plano. Taraven aceita `iqcsa44` ou `iqcsa62`; Ether Cora aceita `iqcsa02` ou `iqcsa60`. Exige existência, troca habilitada, raça e mapa corretos, além de uma correspondência única por código. Versões pessoais não são aceitas. Se duas variantes transferíveis estiverem na bag, solicita conferência em vez de escolher uma silenciosamente.
- A quantidade deve caber em uma pilha de origem por carta. A conferência calcula quantas cartas as pilhas podem atender, sem juntar duas pilhas na mesma carta.
- `teleport-start`: idempotente pelo identificador do lote. Há apenas um lote ativo por ponte. Para cada personagem, prepara/envia um tipo e aguarda a confirmação antes do outro.
- A candidata privada confere remetente, destinatário, título `1`, código/registro do item, quantidade e reserva da bag antes dos handlers nativos de Send e OK. Não constrói pacotes de rede nem contorna a taxa ou as restrições do correio.
- Há pausas de 2 segundos antes de Send, 2 antes de OK e 3 após liberar o resultado. Uma carta só recebe `sent` após o hook existente validar o pacote exato e a resposta de sucesso do jogo.
- Timeout, alteração da carta, rejeição, troca de personagem ou resultado desconhecido interrompem o lote. Não há reenvio automático da carta incerta.
- Pausar/cancelar aguarda terminar a carta em andamento; impede iniciar as seguintes. Um lote incerto não pode ser retomado. Um novo lote exige conferir o envio anterior e revisar sua seleção.

## Persistência e limites

O histórico é local ao PC da ponte, em arquivos privados `teleport-batches/<id>.json`, gravados atomicamente antes de cada comando. Não é um histórico central compartilhado entre admins de PCs diferentes. Após reiniciar a ponte, nenhum lote é retomado automaticamente: recibos confirmados são preservados e operações pendentes ficam bloqueadas para conferência.

Se o navegador fechar, a ponte continua o lote já autorizado. Para interromper as próximas cartas, use **Pausar após esta carta** ou **Cancelar restantes**. A janela consulta o andamento local somente enquanto há um lote em execução; não adiciona polling aos dados de equipamentos do site.

A extensão de envio e seus testes/binários permanecem exclusivamente na variante privada. Nenhuma DLL acompanha a alteração do site. Instalar somente pelo instalador privado autorizado, com jogo e launcher fechados, preservando backup e o componente atual.

## Validação local

- Testes frontend: seleção exata de nível 50, exclusão do remetente, quantidades, filtro de admins, ausência de repetição no timeout e sequência conferir → iniciar.
- Testes da ponte com funções de jogo simuladas: estoque/variantes, ordem, recibos, cancelamento, pausa/retomada, falha de disco, reinício, comandos duplicados e origem HTTP não autorizada.
- Testes x86 privados e suíte de reconstrução: validação do compositor, confirmação nativa, item/quantidade/destinatário incorretos, troca de sessão e regressões das funções existentes.
- Prévia de navegador testada com dois envios simulados concluídos e um lote interrompido na primeira confirmação incerta. Nenhum item real foi enviado.

A validação deste pacote é automatizada e simulada. O envio real em jogo ainda precisa de uma conferência operacional com destinatário e quantidades autorizados; não foi executado durante a preparação deste PR. Ver [publicação conjunta](SITE_MAIL_RELEASE.md).
