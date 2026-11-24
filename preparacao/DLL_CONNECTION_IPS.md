# Consulta administrativa de IPs da DLL

## Comportamento

A ingestão existente grava o IP do último upload em character_status.upload_ip e usa updated_at como horário recebido no servidor. Não precisa atualizar a DLL. Não há histórico adicional de IPs; a coluna nullable é preenchida apenas após novos uploads. Um upload sem origem identificável limpa o IP anterior, para não reaproveitar um endereço antigo.

GET /api/dll/connections requer Administrador (ou Líder, conforme a hierarquia existente). Retorna somente personagens aprovados com IP identificado e upload nos últimos três minutos. Normaliza IPv4/IPv6 e deduplica o nome do personagem, inclusive quando há mais de um GID ou cadastro compartilhado. O contador é de personagens distintos, não de abas ou pessoas.

Administração → DLL → Conexões recentes da DLL mostra o retrato da consulta, permite atualizar manualmente e filtrar IPs compartilhados. Não há polling nem envio desses endereços por WebSocket público, ranking ou respostas comuns de equipamentos/personagem. O acesso HTTP às rotas administrativas permanece registrado pelos logs de acesso existentes.

A ingestão atual identifica o personagem pelo nome sem autenticação criptográfica. Assim, os dados são indícios declarados pelo cliente, não evidência de identidade nem base para banimento automático. IP compartilhado também pode representar VPN, CGNAT ou pessoas na mesma rede.

## Publicação

1. Publicar backend com a migração aditiva V142 e o frontend desta alteração.
2. Configurar DLL_TRUSTED_PROXIES com os IPs/CIDRs REAIS do proxy reverso que se conecta ao backend no Coolify. Exemplo apenas se essa for a rede confirmada: 172.18.0.0/16. Não confiar em 0.0.0.0/0 ou ::/0.
3. O padrão confia somente em loopback. Se o backend receber conexões de um proxy privado não configurado, o IP fica indisponível; isso evita agrupar toda a guilda pelo IP do próprio proxy.
4. O resolver percorre X-Forwarded-For da direita para a esquerda, apenas enquanto o salto atual for confiável. Cabeçalhos enviados por clientes diretos são ignorados; nomes DNS, endereços inválidos e cabeçalhos excessivos são rejeitados sem consulta DNS.
5. Aguardar novos uploads e atualizar a consulta. Informações anteriores à publicação não podem ser reconstruídas por esta coluna.

Nenhuma DLL privada, ponte de cartas ou função de dividir loot faz parte desta alteração. Não foi feito deploy nem alterado dado de produção durante o desenvolvimento.

## Validação desta implementação

- 14 testes backend aprovados em PostgreSQL isolado: captura HTTP, IPv4/IPv6, proxies confiáveis, cabeçalhos falsificados, expiração, deduplicação, ausência de IP em DTO público e autorização.
- 9 testes frontend/i18n aprovados; build TypeScript/Vite aprovado.
- Backend da prévia recompilado e reiniciado na porta 8080, banco guild_bd_preview local. HTTP real: saúde UP, administrador 200, membro 403, anônimo 401.
- Prévia: http://127.0.0.1:15179/admin/dll. Não há dados antigos de IP para preencher a consulta retroativamente.
- Arquivos desta mudança listados em outputs/dll-ip-files.json; as alterações anteriores de assunto de cartas continuam separadas e não pertencem a esta implementação.
