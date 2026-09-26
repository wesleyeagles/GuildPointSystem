# Party system — produção (Coolify)

## Sintoma

Party funciona em local, mas em produção falha (500, 404 ou deploy do backend não sobe).

## Causa comum

No perfil `prod`, Flyway carrega **duas** pastas: `db/migration` e `db/migration-prod`.

Antes da correção, existiam **duas migrations com versão `V23`** (`member_level` e `prod_initial_member`). Isso impede o Flyway de subir e **bloqueia a `V24` (tabelas de party)** — o Coolify pode manter o container antigo “UP” enquanto deploys novos falham.

A migration de level foi renomeada para **`V23.1__member_level.sql`** para eliminar o conflito.

## O que fazer no VPS (Coolify)

1. **Push** da `main` com a correção e aguarde o redeploy do backend (ou dispare deploy manual).
2. Abra **Logs** do container backend e confirme linhas como:
   - `Migrating schema "public" to version "23.1 - member level"` (se ainda não aplicada)
   - `Migrating schema "public" to version "24 - party system"`
3. Se o deploy falhar no Flyway, no PostgreSQL de produção (`guild_points`):

```sql
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;
```

4. Se **`24 - party system`** não aparecer, aplique manualmente o conteúdo de  
   `backend/src/main/resources/db/migration/V24__party_system.sql`  
   e registre no Flyway (somente se souber o que está fazendo):

```sql
INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES (
  (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
  '24', 'party system', 'SQL', 'V24__party_system.sql', NULL, 'manual', NOW(), 0, true
);
```

5. Reinicie o backend no Coolify.

## Dev local (quem já rodou a antiga `V23__member_level`)

Se o backend local falhar no Flyway após o rename:

```sql
UPDATE flyway_schema_history
SET version = '23.1', script = 'V23.1__member_level.sql', description = 'member level'
WHERE version = '23' AND script LIKE '%member_level%';
```

## Validar

- Frontend: `https://blacklist.guildsystem.com.br/party`
- Logado: criar PT e entrar na LFG sem 500
- WebSocket: atualizações em `/topic/parties/GERAL`
