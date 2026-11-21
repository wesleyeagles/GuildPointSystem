# Recompensa por rodada de evento

No painel **Últimos pontos**, administradores encontram **Enviar recompensa** nas rodadas de evento que possuem resgates. Funciona também para PBs automáticos, cujos EventClaim não possuem GuildEvent.

A janela lista quem efetivamente recebeu pontos, sem restrição de nível, agrupando cada personagem uma única vez. É possível desmarcar participantes, alterar o remetente e escolher de 1 a 255 unidades por personagem. O item é exclusivamente `ipcss04`, a mesma versão do lote #049 (o EDF a chama de Cash shop points [499]).

O POST autenticado `/api/events/claims/recipients` valida permissão ADMINISTRADOR+, IDs positivos e distintos, resgates existentes/não negados e pontos positivos. Confere mesma rodada/objetivo; resgates sem GuildEvent seguem o intervalo de dez minutos do histórico. Essa lista é reconferida imediatamente antes de planejar as cartas. Nenhum ponto ou equipamento é alterado e não há migração nova.

A ponte reaproveita a fila dos teleportes: estoque suficiente em pilhas utilizáveis, identidade exata EDF `[13,746,3255964164]`, uma carta por vez, handlers nativos de Send/OK, confirmação real de destinatário/item/quantidade antes da próxima. Pausa/cancelamento são respeitados entre cartas. Reserva planejada e ainda não iniciada pode ser cancelada. Timeout, falha, tráfego estranho, rejeição ou reinício interrompem sem repetir o envio incerto.

O diário local vincula cada carta aos claimIds. Impede nova reserva para resgates já reservados, enviados ou incertos, inclusive após renomear personagem. Cartas canceladas antes de iniciar permitem um novo lote. O bloqueio abrange todos os arquivos de histórico, mesmo quando não estão entre os vinte últimos exibidos.

## Limite e ativação

O histórico de cartas fica **na ponte deste PC**, em `teleport-batches`; não é um bloqueio distribuído entre computadores. Manter o mesmo PC e preservar o diário ao continuar uma distribuição. A lista de quem ganhou pontos vem do backend, não do diário local.

Este pacote inclui o código do site e do backend. Para uso real são necessários frontend/backend publicados e a ponte/variante privada compatíveis neste PC. A DLL privada não acompanha o PR e não deve ser substituída por uma DLL pública. Não foram enviados itens reais durante a preparação deste pacote. Ver [publicação conjunta](SITE_MAIL_RELEASE.md).

## Validação

Testes de UI/hook: permissões, resgates, revalidação antes da ponte, seleção sem limite de nível, quantidade, exclusão, bloqueio por claimId/renomeação, cancelamento de reserva e regressões de equipamentos/teleportes.
Backend: unidade com autenticação real do SecurityContext, deduplicação, negação, pontos não positivos, rodadas diferentes, PBs automáticos e acesso indevido. Sem banco compartilhado.
Ponte: envios sequenciais simulados, confirmação/incerteza, reserva/cancelamento, IDs repetidos e códigos exatos; EDF real conferido.
Candidata privada: suíte completa de reload/guardião/estado/componentes e teste nativo de allowlist/estoque; sem tocar no jogo.
